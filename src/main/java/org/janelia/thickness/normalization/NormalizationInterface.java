package org.janelia.thickness.normalization;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.list.ListImg;
import net.imglib2.type.numeric.RealType;

public interface NormalizationInterface {
	
	public < T extends RealType< T > > void normalize( final RandomAccessibleInterval< T > input );
	
	public void normalize ( final ListImg< double[] > input );

}
