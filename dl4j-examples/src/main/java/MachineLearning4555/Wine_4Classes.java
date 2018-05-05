package MachineLearning4555;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;

import java.io.File;

//This version classifies entries into 3 classes
// 0 if score <85
// 1 if score is between 85 and 99 (including 85 and 99)
// 1 if score is between 90 and 94 (including 90 and 94)
// 2 if score >94
public class Wine_4Classes {

    //Main function. This is what should be run
    public static void main(String[] args) throws Exception {
        //seed for RNG. used for reproducibility
        int seed = 123;
        double learningRate = 0.005;
        int batchSize = 1000;
        int nEpochs = 50;

        //number of non-label columns in data set
        int numInputs = 2;
        //number of classes that can be output
        int numOutputs = 4;
        int numHiddenNodes = 20;

        //Data sets
        final String filenameTrain  = new ClassPathResource("4555_Project/wine_6/winemag-data-SET_6-TRAIN.csv").getFile().getPath();
        final String filenameTest  = new ClassPathResource("4555_Project/wine_6/winemag-data-SET_6-TEST.csv").getFile().getPath();

        //Load the training data:
        RecordReader rr = new CSVRecordReader();
        rr.initialize(new FileSplit(new File(filenameTrain)));
        DataSetIterator trainIter = new RecordReaderDataSetIterator(rr,batchSize,0,4);

        //Load the test/evaluation data:
        RecordReader rrTest = new CSVRecordReader();
        rrTest.initialize(new FileSplit(new File(filenameTest)));
        DataSetIterator testIter = new RecordReaderDataSetIterator(rrTest,batchSize,0,4);

        //Configure the NN
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(seed)
            .updater(new Nesterovs(learningRate, 0.9))
            .list()
            .layer(0, new DenseLayer.Builder().nIn(numInputs).nOut(numHiddenNodes)
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.RELU)
                .build())
            .layer(1, new DenseLayer.Builder()
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.RELU)
                .nIn(numHiddenNodes).nOut(numHiddenNodes).build())
            .layer(2, new DenseLayer.Builder()
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.ELU)
                .nIn(numHiddenNodes).nOut(numHiddenNodes).build())
            .layer(3, new OutputLayer.Builder(LossFunction.NEGATIVELOGLIKELIHOOD)
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.SOFTMAX).weightInit(WeightInit.XAVIER)
                .nIn(numHiddenNodes).nOut(numOutputs).build())
                .pretrain(false).backprop(true).build();

        //Build the NN
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        //Print score every 50 parameter updates
        model.setListeners(new ScoreIterationListener(50));


        for ( int n = 0; n < nEpochs; n++) {
            model.fit( trainIter );
        }

        System.out.println("Evaluate model....");
        Evaluation eval = new Evaluation(numOutputs);
        while(testIter.hasNext()){
            DataSet t = testIter.next();
            INDArray features = t.getFeatureMatrix();
            INDArray lables = t.getLabels();
            INDArray predicted = model.output(features,false);

            eval.eval(lables, predicted);

        }

        //Print the evaluation statistics
        System.out.println(eval.stats());


        //------------------------------------------------------------------------------------
        //Training is complete. Code that follows is for plotting the data & predictions only

        //Plot the data:
        double xMin = -1;
        double xMax = 2;
        double yMin = 0;
        double yMax = 400;


        //Let's evaluate the predictions at every point in the x/y input space
        int nPointsPerAxis = 1000;
        double[][] evalPoints = new double[nPointsPerAxis*nPointsPerAxis][2];
        int count = 0;
        for( int i=0; i<nPointsPerAxis; i++ ){
            for( int j=0; j<nPointsPerAxis; j++ ){
                double x = i * (xMax-xMin)/(nPointsPerAxis-1) + xMin;
                double y = j * (yMax-yMin)/(nPointsPerAxis-1) + yMin;

                evalPoints[count][0] = x;
                evalPoints[count][1] = y;

                count++;
            }
        }

        INDArray allXYPoints = Nd4j.create(evalPoints);
        INDArray predictionsAtXYPoints = model.output(allXYPoints);

        //Get all of the training data in a single array, and plot it:
        rr.initialize(new FileSplit(new ClassPathResource("4555_Project/wine_6/winemag-data-SET_6-TRAIN.csv").getFile()));
        rr.reset();
        int nTrainPoints = 20000;
        trainIter = new RecordReaderDataSetIterator(rr,nTrainPoints,0, 4);
        DataSet ds = trainIter.next();
        PlotUtil.plotTrainingData(ds.getFeatures(), ds.getLabels(), allXYPoints, predictionsAtXYPoints, nPointsPerAxis);


        //Get test data, run the test data through the network to generate predictions, and plot those predictions:
        rrTest.initialize(new FileSplit(new ClassPathResource("4555_Project/wine_6/winemag-data-SET_6-TEST.csv").getFile()));
        rrTest.reset();
        int nTestPoints = 4000;
        testIter = new RecordReaderDataSetIterator(rrTest,nTestPoints,0,4);
        ds = testIter.next();
        INDArray testPredicted = model.output(ds.getFeatures());
        PlotUtil.plotTestData(ds.getFeatures(), ds.getLabels(), testPredicted, allXYPoints, predictionsAtXYPoints, nPointsPerAxis);

        System.out.println("Complete");
    }
}
