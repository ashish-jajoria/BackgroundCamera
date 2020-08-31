/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
package in.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;

import com.nayan.nayanindia.models.LpProbs;
import com.nayan.nayanindia.tflite.env.Logger;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 * <p>
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
public class TFLiteLPDetectionAPIModel implements Classifier {
    private static final Logger LOGGER = new Logger();
    // Only return this many results.
    private static final int NUM_DETECTIONS = 10;
    // Float model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    private boolean isModelQuantized;
    // Config values.
    private Vector<String> labels = new Vector<String>();
    private int[] intValues;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;
    private ByteBuffer imgData;
    private Interpreter tfLite;

    private TFLiteLPDetectionAPIModel() {
    }

    /**
     * Memory-map the model file in Assets.
     */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     * @param inputSize     The size of image input
     * @param isQuantized   Boolean representing model is quantized or not
     */
    public static Classifier create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final int inputSize,
            final boolean isQuantized)
            throws IOException {
        final TFLiteLPDetectionAPIModel d = new TFLiteLPDetectionAPIModel();
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        InputStream labelsInput = assetManager.open(actualFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            LOGGER.w(line);
            d.labels.add(line);
        }
        br.close();
        try {
            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        d.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        d.tfLite.setNumThreads(NUM_THREADS);
        d.outputLocations = new float[1][27][41][8];
        return d;
    }

    @Override
    public List<Recognition> recognizeImage(final Bitmap bitmap) {
        int inputWidth = bitmap.getWidth();
        int inputHeight = bitmap.getHeight();
        int numBytesPerChannel = 4;

        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");
        Trace.beginSection("preprocessBitmap");
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        float ratio = (float) Math.max(inputWidth, inputHeight) / Math.min(inputWidth, inputHeight);
        int side = (int) (ratio * 288);
        int bound_dim = (int) Math.min(side + (side % Math.pow(2, 4)), 608);
        int min_dim_img = Math.min(inputWidth, inputHeight);
        float factor = ((float) bound_dim) / min_dim_img;

        int net_step = (int) Math.pow(2, 4);

        int w = (int) (inputWidth * factor);
        int h = (int) (inputHeight * factor);
        if (w % net_step != 0) {
            w += net_step - w % net_step;
        }
        if (h % net_step != 0) {
            h += net_step - h % net_step;
        }

        int[] array = new int[4];
        array[0] = 1;
        array[1] = h;
        array[2] = w;
        array[3] = 3;
        tfLite.resizeInput(0, array);

        imgData = ByteBuffer.allocateDirect(1 * w * h * 3 * numBytesPerChannel);
        imgData.order(ByteOrder.nativeOrder());
        imgData.rewind();

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, false);

        intValues = new int[w * h];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        for (int i = 0; i < w; ++i) {
            for (int j = 0; j < h; ++j) {
                int pixelValue = intValues[i * h + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((float) (((pixelValue >> 16) & 0xFF)) / 255);
                    imgData.putFloat((float) (((pixelValue >> 8) & 0xFF)) / 255);
                    imgData.putFloat((float) ((pixelValue & 0xFF)) / 255);
                }
            }
        }
        Trace.endSection(); // preprocessBitmap
        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");
        int size1 = 1;
        int size2 = h / net_step;
        int size3 = w / net_step;
        int size4 = 8;
        outputLocations = new float[size1][size2][size3][size4];
        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);

        Trace.endSection();
        // Run the inference call.
        Trace.beginSection("run");
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();

        float[][][] probs = new float[size1][size2][size3];
        for (int i = 0; i < size2; i++) {
            for (int j = 0; j < size3; j++) {
                probs[0][i][j] = ((float[][][][]) Objects.requireNonNull(outputMap.get(0)))[0][i][j][0];
            }
        }

        float[][][][] affines = new float[size1][size2][size3][6];
        for (int i = 0; i < size2; i++) {
            for (int j = 0; j < size3; j++) {
                for (int k = 0; k < 6; k++) {
                    affines[0][i][j][k] = ((float[][][][]) Objects.requireNonNull(outputMap.get(0)))[0][i][j][k + 2];
                }
            }
        }

        int rx = size2;
        int ry = size3;
        float threshold = 0.5f;

        ArrayList<LpProbs> lpProbs = new ArrayList<>();

        for (int i = 0; i < size2; i++) {
            for (int j = 0; j < size3; j++) {
                if (probs[0][i][j] < threshold) {
                    continue;
                }

                float[][] A = new float[2][3];
                float prob = probs[0][i][j];

                A[0][0] = Math.max(0, affines[0][i][j][0]);
                A[0][1] = affines[0][i][j][1];
                A[0][2] = affines[0][i][j][2];
                A[1][0] = affines[0][i][j][3];
                A[1][1] = Math.max(0, affines[0][i][j][4]);
                A[1][2] = affines[0][i][j][5];

                float m = j + 0.5f;
                float n = i + 0.5f;

                float[][] base = {{-0.5f, 0.5f, 0.5f, -0.5f}, {-0.5f, -0.5f, 0.5f, 0.5f}, {1, 1, 1, 1}};

                float[][] pts = new float[2][4];
                pts[0][0] = ((A[0][0] * base[0][0] + A[0][1] * base[1][0] + A[0][2] * base[2][0]) * 7.75f + m) / ry;
                pts[0][1] = ((A[0][0] * base[0][1] + A[0][1] * base[1][1] + A[0][2] * base[2][1]) * 7.75f + m) / ry;
                pts[0][2] = ((A[0][0] * base[0][2] + A[0][1] * base[1][2] + A[0][2] * base[2][2]) * 7.75f + m) / ry;
                pts[0][3] = ((A[0][0] * base[0][3] + A[0][1] * base[1][3] + A[0][2] * base[2][3]) * 7.75f + m) / ry;
                pts[1][0] = ((A[1][0] * base[0][0] + A[1][1] * base[1][0] + A[1][2] * base[2][0]) * 7.75f + n) / rx;
                pts[1][1] = ((A[1][0] * base[0][1] + A[1][1] * base[1][1] + A[1][2] * base[2][1]) * 7.75f + n) / rx;
                pts[1][2] = ((A[1][0] * base[0][2] + A[1][1] * base[1][2] + A[1][2] * base[2][2]) * 7.75f + n) / rx;
                pts[1][3] = ((A[1][0] * base[0][3] + A[1][1] * base[1][3] + A[1][2] * base[2][3]) * 7.75f + n) / rx;


                float tl_x = 100, tl_y = 100;
                float br_x = -100, br_y = -100;

                for (int k = 0; k < 4; k++) {
                    tl_x = Math.min(tl_x, pts[0][k]);
                    tl_y = Math.min(tl_y, pts[1][k]);

                    br_x = Math.max(br_x, pts[0][k]);
                    br_y = Math.max(br_y, pts[1][k]);
                }

                lpProbs.add(new LpProbs(probs[0][i][j], tl_x, tl_y, br_x, br_y));

              //  Log.d("points:", tl_x + "," + tl_y + "," + br_x + "," + br_y + ", (" + i + "," + j + ")");
            }
            // final_labels =
        }

        Collections.sort(lpProbs, new SortByProbs());

        ArrayList<LpProbs> selectedLabels = getSelectedLabels(lpProbs, 0.1f);

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        for (int i = 0; i < selectedLabels.size(); ++i) {
            final RectF detection =
                    new RectF(
                            selectedLabels.get(i).getTlX() * inputWidth,
                            selectedLabels.get(i).getTlY() * inputHeight,
                            selectedLabels.get(i).getBrX() * inputWidth,
                            selectedLabels.get(i).getBrY()* inputHeight);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            int labelOffset = 1;
            recognitions.add(
                    new Recognition(
                            "" + i,
                            "LP",
                            selectedLabels.get(i).getProb(),
                            detection));
        }
        Trace.endSection(); // "recognizeImage"
        return recognitions;
    }

    private ArrayList<LpProbs> getSelectedLabels(ArrayList<LpProbs> lpProbs, float threshold) {
        ArrayList<LpProbs> selectedLabels = new ArrayList<>();

        for (LpProbs label : lpProbs) {
            boolean non_overlap = true;

            for (LpProbs selectedLabel : selectedLabels) {
                if (IOU_Labels(label, selectedLabel) > threshold) {
                    non_overlap = false;
                    break;
                }
            }

            if (non_overlap) {
                selectedLabels.add(label);
            }
        }

        return selectedLabels;
    }

    private float IOU_Labels(LpProbs label, LpProbs selectedLabel) {
        return IOU(label.getTlX(), label.getTlY(), label.getBrX(), label.getBrY(),
                selectedLabel.getTlX(), selectedLabel.getTlY(), selectedLabel.getBrX(), selectedLabel.getBrY());
    }

    private float IOU(float labelTlX, float labelTlY, float labelBrX, float labelBrY,
                      float selectedLabelTlX, float selectedLabelTlY, float selectedLabelBrX, float selectedLabelBrY) {

        float w1 = labelBrX - labelTlX;
        float w2 = selectedLabelBrX - selectedLabelTlX;

        float h1 = labelBrY - labelTlY;
        float h2 = selectedLabelBrY - selectedLabelTlY;

        if (w1 > 0 && w2 > 0 && h1 > 0 && h2 > 0) {
            float intersection_w = (float) Math.max(Math.min(selectedLabelBrX, labelBrX) - Math.max(selectedLabelTlX, labelTlX), 0.0);
            float intersection_h = (float) Math.max(Math.min(selectedLabelBrY, labelBrY) - Math.max(selectedLabelTlY, labelTlY), 0.0);

            float intersection_area = intersection_h * intersection_w;
            float area1 = w1 * h1;
            float area2 = w2 * h2;

            float union_area = area1 + area2 - intersection_area;

            return intersection_area / union_area;
        } else {
            return -1.0f;
        }
    }

    class SortByProbs implements Comparator<LpProbs> {
        // Used for sorting in ascending order of
        // roll name
        public int compare(LpProbs a, LpProbs b) {
            return Float.compare(b.getProb(), a.getProb());
        }
    }

    @Override
    public void enableStatLogging(final boolean logStats) {
    }

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {
    }

    public void setNumThreads(int num_threads) {
        if (tfLite != null) tfLite.setNumThreads(num_threads);
    }

    @Override
    public void setUseNNAPI(boolean isChecked) {
        if (tfLite != null) tfLite.setUseNNAPI(isChecked);
    }
}