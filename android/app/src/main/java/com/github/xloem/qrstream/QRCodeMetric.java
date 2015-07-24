package com.github.xloem.qrstream;

public class QRCodeMetric extends CodeMetric {

    final private static int[] QR_VERSION_CAPACITY = {
            0, // there is no version 0
            17,   32,   53,   78,   106,  134,  154,  192,  230,  271,
            321,  367,  425,  458,  520,  586,  644,  718,  792,  858,
            929,  1003, 1091, 1171, 1273, 1367, 1465, 1528, 1628, 1732,
            1840, 1952, 2068, 2188, 2303, 2431, 2563, 2699, 2809, 2953
    };

    public int getMaxIndex() {
        return QR_VERSION_CAPACITY.length - 1;
    }

    public int getMinIndex() {
        return 1;
    }

    public void setIndex(int version) {
        index = version;
        dimension = 17 * 4 + version;
        capacity = QR_VERSION_CAPACITY[index];
    }
}
