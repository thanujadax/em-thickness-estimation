package org.janelia.correlations;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 * @param <T>
 * @param <U>
 */
public class CrossCorrelation < T extends RealType< T >, U extends RealType< U >, S extends RealType< S > & NativeType< S > > extends AbstractCrossCorrelation< T, U, S > implements RandomAccessibleInterval< S > {



	private final ArrayImg< BitType, LongArray > calculatedCheck;
	private final TYPE type;
	public enum TYPE { STANDARD, SIGNED_SQUARED };

	public CrossCorrelation(final RandomAccessibleInterval<T> img1,
			final RandomAccessibleInterval<U> img2,
			final long[] r,
			final S n ){
		this( img1, img2, r, TYPE.STANDARD, n );
	}


	public CrossCorrelation(final RandomAccessibleInterval<T> img1,
			final RandomAccessibleInterval<U> img2,
			final long[] r,
			final TYPE type,
			final S n ) {
		super( img1, img2, r, n );
		this.calculatedCheck = ArrayImgs.bits( dim );
		this.type = type;
	}

	public class CrossCorrelationRandomAccess extends Point implements RandomAccess< S > {

		private final ArrayRandomAccess< BitType > checkAccess;
		private final ArrayRandomAccess< S > correlationsAccess;

		private final long[] intervalMin;
		private final long[] intervalMax;

		private CrossCorrelationRandomAccess(final long[] position,
				final ArrayRandomAccess<BitType> checkAccess,
				final ArrayRandomAccess< S > correlationsAccess,
				final long[] intervalMin, final long[] intervalMax) {
			super(position);
			this.checkAccess = checkAccess;
			this.correlationsAccess = correlationsAccess;
			this.intervalMin = intervalMin;
			this.intervalMax = intervalMax;
		}

		public CrossCorrelationRandomAccess() {
			super( dim.length );
			this.checkAccess        = calculatedCheck.randomAccess();
			this.correlationsAccess = correlations.randomAccess();

			intervalMin = new long[ dim.length ];
			intervalMax = new long[ dim.length ];
		}

		@Override
		public S get() {
			checkAccess.setPosition( this.position );
			correlationsAccess.setPosition( this.position );

			final S currVal = correlationsAccess.get();

			if ( checkAccess.get().get() == false ) {

				for ( int d = 0; d < this.n; ++d ) {
					intervalMin[d] = Math.max( 0,      this.position[d] - r[d] );
					intervalMax[d] = Math.min( max[d], this.position[d] + r[d] );
				}


				switch ( type ) {
				case STANDARD:
					currVal.setReal( calculateNormalizedCrossCorrelation(
							Views.interval( img1, intervalMin, intervalMax ),
							Views.interval( img2, intervalMin, intervalMax )
							)
							);
					break;
				case SIGNED_SQUARED:
					currVal.setReal( calculateSignedSquaredNormalizedCrossCorrelation(
							Views.interval( img1, intervalMin, intervalMax ),
							Views.interval( img2, intervalMin, intervalMax )
							)
							);
				default:
					break;
				}

			}

			return currVal;

		}

		@Override
		public CrossCorrelationRandomAccess copy() {
			return new CrossCorrelationRandomAccess( this.position.clone(),
					checkAccess.copy(),
					correlationsAccess.copy(),
					intervalMin.clone(),
					intervalMax.clone());
		}

		@Override
		public CrossCorrelationRandomAccess copyRandomAccess() {
			return copy();
		}

	}


	public double calculateNormalizedCrossCorrelation( final RandomAccessibleInterval< T > i1, final RandomAccessibleInterval< U > i2 ) {
		double cc = 0.0;

		long nElements = 1;
		for ( int d = 0; d < i1.numDimensions(); ++d ) {
			nElements *= i1.dimension( d );
		}

		final double nElementsDouble = nElements;

		final double mean1 = calculateSum( i1 ) / nElementsDouble;
		final double mean2 = calculateSum( i2 ) / nElementsDouble;
		final double var1 = calculateSumOfSquaredDifferences( i1, mean1 ) / nElementsDouble;
		final double var2 = calculateSumOfSquaredDifferences( i2, mean2 ) / nElementsDouble;

		final Cursor<T> c1 = Views.flatIterable( i1 ).cursor();
		final Cursor<U> c2 = Views.flatIterable( i2 ).cursor();

		while( c1.hasNext() ) {
			cc += ( c1.next().getRealDouble() - mean1 ) * ( c2.next().getRealDouble() - mean2 );
		}

		return cc / ( Math.sqrt( var1 ) * Math.sqrt( var2 ) * nElementsDouble );
	}


	public double calculateSignedSquaredNormalizedCrossCorrelation ( final RandomAccessibleInterval< T > i1, final RandomAccessibleInterval< U > i2 ) {
		final double cc = calculateNormalizedCrossCorrelation(i1, i2);
		return ( cc < 0 ? -cc * cc : cc * cc );
	}


	public < V extends RealType<V> > double  calculateSum( final RandomAccessibleInterval< V > i ) {
		double sum = 0.0;
		final Cursor< V > c = Views.flatIterable( i ).cursor();
		while ( c.hasNext() ) {
			sum += c.next().getRealDouble();
		}
		return sum;
	}


	public < V extends RealType<V> > double calculateSumOfSquaredDifferences( final RandomAccessibleInterval< V > i, final double mean ) {
		double sum = 0.0;
		final Cursor< V > c = Views.flatIterable( i ).cursor();
		double tmpDiff;
		while( c.hasNext() ) {
			tmpDiff = c.next().getRealDouble() - mean;
			sum    += tmpDiff*tmpDiff;
		}
		return sum;
	}


	@Override
	public CrossCorrelationRandomAccess randomAccess() {
		return new CrossCorrelationRandomAccess();
	}

	@Override
	public CrossCorrelationRandomAccess randomAccess(final Interval interval) {
		return randomAccess();
	}

}
