package org.janelia.correlations.storage;

import ij.ImageJ;

import java.util.Random;

import org.janelia.correlations.AbstractIntegralCrossCorrelation.CrossCorrelationType;
import org.janelia.correlations.CrossCorrelationFactory;
import org.janelia.correlations.AbstractIntegralCrossCorrelation.NotEnoughSpaceException;
import org.janelia.utility.ConstantRealRandomAccesssible;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.Sampler;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.list.ListImg;
import net.imglib2.img.list.ListRandomAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class CorrelationMatricesWithRadius<T extends NativeType<T> & RealType<T>>
		implements RandomAccessibleInterval<RandomAccessibleInterval< T > > {
	
	private long[] radius;
	private long   range;
	private final ListImg< RandomAccessibleInterval<T> > correlations;
	private final long[] dim;
	private final long[] max;
	private final long zRange;
	private final T dummy;
	
	public < U extends RealType< U > > CorrelationMatricesWithRadius( 
			final RandomAccessibleInterval< U > imageStack, 
			long[] radius, 
			long range,
			final CrossCorrelationFactory< U, U, T > factory,
			final T dummy ) throws NotEnoughSpaceException {
		super();
		this.radius = radius;
		this.range  = range;
		this.dim    = new long[] { imageStack.dimension( 0 ), imageStack.dimension( 1 ) };
		this.max    = new long[] { dim[0] - 1, dim[1] - 1};
		this.zRange = imageStack.dimension( 2 );
		this.dummy  = dummy;
		this.correlations = generate( imageStack, this.radius, this.range, this.dim, this.zRange, factory, this.dummy );
	}
	
	public static < U extends RealType< U >, V extends NativeType< V > & RealType< V > > ListImg< RandomAccessibleInterval< V > > generate(
			final RandomAccessibleInterval< U > imageStack,
			long[] radius,
			long range,
			long[] dim,
			long zRange,
			final CrossCorrelationFactory< U, U, V > factory,
			V dummy
			) throws NotEnoughSpaceException
	{
		V nanDummy = dummy.copy();
		V oneDummy = dummy.copy();
		nanDummy.setReal( Double.NaN );
		oneDummy.setReal( 1.0 );
		IntervalView<V> constantNaNImage = 
				Views.interval( Views.raster( new ConstantRealRandomAccesssible< V >( 2, nanDummy ) ), new FinalInterval( dim ) );
		IntervalView<V> constantOneImage = 
				Views.interval( Views.raster( new ConstantRealRandomAccesssible< V >( 2, oneDummy ) ), new FinalInterval( dim ) );
		ListImg< RandomAccessibleInterval< V > > correlations = 
				new ListImg< RandomAccessibleInterval< V > >( new long[] { zRange,  zRange }, constantNaNImage );
		
		ListRandomAccess<RandomAccessibleInterval<V>> ra1 = correlations.randomAccess();
		ListRandomAccess<RandomAccessibleInterval<V>> ra2 = correlations.randomAccess();
		
		for ( int z1 = 0; z1 < zRange; ++z1 ) {
			ra1.setPosition( z1, 0 );
			ra1.setPosition( z1, 1 );
			ra2.setPosition( z1, 1 );
			ra1.set( constantOneImage );
			IntervalView<U> img1 = Views.hyperSlice( imageStack, 2, z1 );
			for ( int z2 = z1 + 1; z2 < zRange; ++z2 ) {
				final RandomAccessibleInterval< V > cc;
				if ( Math.abs( z1 - z2 ) > range )
					cc = constantNaNImage;
				else {
					IntervalView<U> img2 = Views.hyperSlice( imageStack, 2, z2 );
					cc = factory.create( img1, img2, radius );
				}
				ra1.setPosition( z2, 1 );
				ra2.setPosition( z2, 0 );
				ra1.set( cc );
				ra2.set( cc );
			}
		}
		
		return correlations;
	}
	
	
	public class MatrixStrip implements RandomAccessibleInterval< T > {
		
		private final long[] dim = new long[] { zRange, zRange };
		private final long[] max = new long[] { zRange - 1, zRange - 1 };
		private final int numDimensions = 2;
		private final long[] xy;
		private final ListImg< RandomAccessibleInterval<T> > ccs = correlations;
		
		

		public MatrixStrip( long[] xy ) {
			super();
			this.xy = xy;
		}
		
		/**
		 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
		 *
		 */
		public class MatrixStripRandomAccess extends Point implements RandomAccess< T > {
			
			final ListRandomAccess<RandomAccessibleInterval<T>> ra = ccs.randomAccess();
			
			public MatrixStripRandomAccess() {
				super( 2 );
			}
			
			@Override
			public T get() {
				ra.setPosition( position );
				RandomAccess<T> imgRa = ra.get().randomAccess();
				imgRa.setPosition( xy );
				return imgRa.get();
			}

			@Override
			public MatrixStripRandomAccess copy() {
				return copyRandomAccess();
			}

			@Override
			public MatrixStripRandomAccess copyRandomAccess() {
				MatrixStripRandomAccess copy = new MatrixStripRandomAccess();
				copy.setPosition( this.position );
				return copy;
			}

			
			
		}

		@Override
		public RandomAccess<T> randomAccess() {
			return new MatrixStripRandomAccess();
		}

		@Override
		public RandomAccess<T> randomAccess(Interval interval) {
			return randomAccess();
		}

		@Override
		public int numDimensions() {
			return numDimensions;
		}

		@Override
		public long min(int d) {
			return 0;
		}

		@Override
		public void min(long[] min) {
			for (int d = 0; d < numDimensions; ++d )
				min[d] = 0;
		}

		@Override
		public void min(Positionable min) {
			for ( int d = 0; d < numDimensions; ++d )
				min.setPosition( 0, d );
		}

		@Override
		public long max(int d) {
			return this.max[d];
		}

		@Override
		public void max(long[] max) {
			for (int d = 0; d < max.length; d++)
				max[d] = this.max[d];
		}

		@Override
		public void max(Positionable max) {
			for (int d = 0; d < numDimensions; ++d )
				max.setPosition( this.max[d], d);
		}

		@Override
		public double realMin(int d) {
			return min(d);
		}

		@Override
		public void realMin(double[] min) {
			for ( int d = 0; d < min.length; ++d )
 				min[d] = 0.0;
		}

		@Override
		public void realMin(RealPositionable min) {
			this.min( min );
		}

		@Override
		public double realMax(int d) {
			return max( d );
		}

		@Override
		public void realMax(double[] max) {
			for (int d = 0; d < max.length; ++d )
				max[d] = this.max[d];
		}

		@Override
		public void realMax(RealPositionable max) {
			this.max( max );
		}

		@Override
		public void dimensions(long[] dimensions) {
			for (int d = 0; d < dimensions.length; d++) {
				dimensions[ d ] = dim[ d ];
			}
		}

		@Override
		public long dimension(int d) {
			return dim[ d ];
		}
		
	}
	

	public long[] getRadius() {
		return radius;
	}

		public long getRange() {
		return range;
	}
	
	public class CorrelationsRandomAccess extends Point implements RandomAccess< RandomAccessibleInterval<T> > {
		
		public CorrelationsRandomAccess() {
			super( 2 );
		}

		@Override
		public RandomAccessibleInterval<T> get() {
			CorrelationMatricesWithRadius<T>.MatrixStrip matrix = new MatrixStrip( position );
			return matrix;
		}

		@Override
		public Sampler<RandomAccessibleInterval<T>> copy() {
			return copyRandomAccess();
		}

		@Override
		public RandomAccess<RandomAccessibleInterval<T>> copyRandomAccess() {
			CorrelationMatricesWithRadius<T>.CorrelationsRandomAccess copy = 
					new CorrelationsRandomAccess();
			copy.setPosition( position );
			return copy;
		}
		
	}

	@Override
	public RandomAccess<RandomAccessibleInterval<T>> randomAccess() {
		return new CorrelationsRandomAccess();
	}

	@Override
	public RandomAccess<RandomAccessibleInterval<T>> randomAccess(
			Interval interval) {
		return randomAccess();
	}

	@Override
	public int numDimensions() {
		return dim.length;
	}

	@Override
	public long min(int d) {
		return 0;
	}

	@Override
	public void min(long[] min) {
		for (int d = 0; d < min.length; d++) {
			min[d] = 0;
		}
	}

	@Override
	public void min(Positionable min) {
		for (int d = 0; d < dim.length; d++) {
			min.setPosition( 0, d );
		}
	}

	@Override
	public long max( int d ) {
		return max[ d ];
	}

	@Override
	public void max(long[] max) {
		for (int d = 0; d < max.length; d++) {
			max[d] = this.max[ d ];
		}
	}

	@Override
	public void max(Positionable max) {
		for (int d = 0; d < dim.length; d++) {
			max.setPosition( this.max[d], d );
		}
	}

	@Override
	public double realMin(int d) {
		return min(d);
	}

	@Override
	public void realMin(double[] min) {
		for (int d = 0; d < min.length; d++) {
			min[d] = 0;
		}
	}

	@Override
	public void realMin(RealPositionable min) {
		this.min(min);
	}

	@Override
	public double realMax(int d) {
		return max(d);
	}

	@Override
	public void realMax(double[] max) {
		for (int d = 0; d < max.length; d++) {
			max[d] = this.max[d];
		}
	}

	@Override
	public void realMax(RealPositionable max) {
		this.max( max );
	}

	@Override
	public void dimensions(long[] dimensions) {
		for (int d = 0; d < dimensions.length; d++) {
			dimensions[ d ] = dim[ d ];
		}
	}

	@Override
	public long dimension(int d) {
		return dim[ d ];
	}

	
	public static void main(String[] args) throws NotEnoughSpaceException {
		
		final long[] dim = new long[] { 29, 29, 100 };
		Random rng = new Random( 100 );
		ArrayImg<DoubleType, DoubleArray> imageStack = ArrayImgs.doubles( dim );
		
		for ( DoubleType i : imageStack )
			i.set( rng.nextDouble() );
		
		int range = (int) Math.sqrt( dim[2] );
		
		long[] radius = new long[] { 5, 5 };
		
		CrossCorrelationFactory<DoubleType, DoubleType, DoubleType> ccFactory =
				new CrossCorrelationFactory< DoubleType, DoubleType, DoubleType >( new DoubleType() );
		
		CorrelationMatricesWithRadius<DoubleType> cwr = 
				new CorrelationMatricesWithRadius<DoubleType>(imageStack, radius, range, ccFactory, new DoubleType() );
		
		RandomAccess<RandomAccessibleInterval<DoubleType>> ra = cwr.randomAccess();
		long[] cwrDim = new long[ 2 ];
		cwr.dimensions( cwrDim );
		ra.setPosition( new long[] { 28, 28 } );
		RandomAccessibleInterval<DoubleType> mat = ra. get();
		new ImageJ();
		ImageJFunctions.show( imageStack );
		ImageJFunctions.show( mat );
		ra.setPosition( new long[] { 5, 23 } );
		RandomAccessibleInterval<DoubleType> mat2 = ra.get();
		ImageJFunctions.show( mat2 );
		
	}

}
