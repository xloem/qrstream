package com.github.xloem.qrstream;

public class Metrics {

    
    final private static int[] QR_VERSION_CAPACITY = {
        0, // there is no version 0
        17,   32,   53,   78,   106,  134,  154,  192,  230,  271,
        321,  367,  425,  458,  520,  586,  644,  718,  792,  858,
        929,  1003, 1091, 1171, 1273, 1367, 1465, 1528, 1628, 1732,
        1840, 1952, 2068, 2188, 2303, 2431, 2563, 2699, 2809, 2953
    };

    public static int qrcodeCapacity(int dimension) {

        // pick the largest version # with an equal or smaller dimension
        int versionDimension, version = QR_VERSION_CAPACITY.length;
        do {
            -- version;
            versionDimension = 17 + 4 * version;
        } while (versionDimension > dimension && version > 1);

        return QR_VERSION_CAPACITY[version];

    }


    final private static int[] AZTEC_LAYERS_DIMENSION = {
        15,  19,  23,  27, // [0,1,2,3] = compact[1,2,3,4]
        // [4,...] = normal[4,...]
                       31,  37,  41,  45,  49,
        53,  57,  61,  67,  71,  75,  79,  83,
        87,  91,  95,  101, 105, 109, 113, 117,
        121, 125, 131, 135, 139, 143, 147, 151
    };

    // from http://www.barcodephp.com/en/aztec/technical
    // TODO: is this inaccurate if zxing uses a different error correction %?
    final private static int[] AZTEC_LAYERS_CAPACITY = {
        6,    19,   33,   89, // [0,1,2,3] = compact[1,2,3,4]
        // [4,...] = normal[4,...]
                          62,   87,   114,  145,  179,
        214,  256,  298,  343,  394,  446,  502,  559,
        621,  687,  753,  824,  898,  976,  1056, 1138,
        1224, 1314, 1407, 1501, 1600, 1702, 1806, 1914,  
    };

    public static int aztecCapacity(int dimension) {
        
        // pick the most layers with an equal or smaller dimension
        int layersDimension, layers = AZTEC_LAYERS_DIMENSION.length;
        do {
            -- layers;
            layersDimension = AZTEC_LAYERS_DIMENSION[layers];
        } while (layersDimension > dimension && layers > 0);

        return AZTEC_LAYERS_CAPACITY[layers];
    }

}
