package com.github.xloem.qrstream;

import com.google.zxing.BarcodeFormat;

// TODO: Add Error Correction Level

public abstract class CodeMetric {

    // Code-specific integer indicating the size
    protected int index;

    // Number of cells wide or tall this size is
    protected int dimension;

    // Maximum number of bytes this size can promise to hold
    protected int capacity;

    // Quiet zone displayed around the code
    private int margin;


    // Accessors for the index
    public int getIndex() { return index; }
    public abstract void setIndex(int index);
    public abstract int getMaxIndex();
    public abstract int getMinIndex();

    // Incrementally increase size
    public void grow() {
        int idx = getIndex();
        if (idx < getMaxIndex())
            setIndex(idx + 1);
        else
            throw new IndexOutOfBoundsException();
    }

    // Incrementally decrease size
    public void shrink() {
        int idx = getIndex();
        if (idx > getMinIndex())
            setIndex(idx - 1);
        else
            throw new IndexOutOfBoundsException();
    }


    // Accessors for the dimension
    public int getDimension() { return dimension + margin * 2; }

    // Set to the largest values that give an equal or smaller dimension
    public void setDimension(int dimension) {
        setIndex(getMaxIndex());
        int minIndex = getMinIndex();

        dimension -= margin * 2;

        while (this.dimension > dimension && index > minIndex) {
            setIndex(index - 1);
        }
    }


    // Accessors for the capacity
    public int getCapacity() { return capacity; }

    // Set to the smallest values that give an equal or greater capacity
    public void setCapacity(int capacity) {
        setIndex(getMinIndex());
        int maxIndex = getMaxIndex();

        while (this.capacity < capacity && index < maxIndex) {
            setIndex(index + 1);
        }
    }


    // Accessors for the margin
    // Note that changing the margin will change the dimension !!
    // TODO: should this shrink the barcode to have the same dimension?
    public int getMargin() { return margin; }
    public void setMargin(int margin) { this.margin = margin; }


    // Construct given a BarcodeFormat
    public static CodeMetric create(BarcodeFormat format) {
        if (format == BarcodeFormat.QR_CODE)
            return new QRCodeMetric();
        else if (format == BarcodeFormat.AZTEC)
            return new AztecMetric();
        else
            throw new IllegalArgumentException();
    }
}
