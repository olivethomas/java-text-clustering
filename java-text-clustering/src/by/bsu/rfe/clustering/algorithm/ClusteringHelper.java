package by.bsu.rfe.clustering.algorithm;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import test.by.bsu.rfe.clustering.app.util.CSVDataSetExporter;
import by.bsu.rfe.clustering.algorithm.datamodel.CentroidCluster;
import by.bsu.rfe.clustering.algorithm.datamodel.Cluster;
import by.bsu.rfe.clustering.algorithm.datamodel.DataElement;
import by.bsu.rfe.clustering.math.DoubleVector;
import by.bsu.rfe.clustering.math.MathUtil;
import by.bsu.rfe.clustering.text.data.DocumentDataElement;
import by.bsu.rfe.clustering.text.data.DocumentDataSet;
import by.bsu.rfe.clustering.text.ir.Document;
import by.bsu.rfe.clustering.text.ir.DocumentCollection;
import by.bsu.rfe.clustering.text.vsm.DocumentVSMGenerator;
import by.bsu.rfe.clustering.text.vsm.TFIDF;

import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.Sets;

public final class ClusteringHelper {

  private ClusteringHelper() {
  }

  public static <E extends DataElement> double computeSquareError(CentroidCluster<E> cluster) {
    List<E> elements = cluster.getDataElements();

    if (elements.isEmpty()) {
      return 0;
    }

    DoubleVector center = cluster.computeCentroid();

    double totalError = 0;
    final int vectorSize = center.size();

    for (DataElement e : elements) {
      DoubleVector elemVector = e.asVector();
      double errorWithinCluster = 0;

      for (int i = 0; i < vectorSize; i++) {
        errorWithinCluster += MathUtil.square(elemVector.get(i) - center.get(i));
      }

      totalError += errorWithinCluster;
    }

    return totalError;
  }

  public static void assignLabels(List<Cluster<DocumentDataElement>> clusterData, DocumentDataSet dataSet) {
    for (Cluster<DocumentDataElement> cluster : clusterData) {
      MinMaxPriorityQueue<TermEntry> queue = MinMaxPriorityQueue.orderedBy(new Comparator<TermEntry>() {

        @Override
        public int compare(TermEntry o1, TermEntry o2) {
          return -Double.compare(o1.getScore(), o2.getScore());
        }
      }).maximumSize(5).create();

      DocumentCollection localCollection = new DocumentCollection();
      for (DocumentDataElement elem : cluster.getDataElements()) {
        localCollection.addDocument(elem.getDocument());
      }

      DocumentVSMGenerator docToVsm = new TFIDF();
      DocumentDataSet clusterDataSet = docToVsm.createVSM(localCollection);
      // TODO remove this
      try {
        CSVDataSetExporter.export(clusterDataSet, new File("tmp/" + cluster.getLabel() + ".csv"));
      }
      catch (IOException e) {
      }

      for (DocumentDataElement elem : clusterDataSet.elements()) {
        Document document = elem.getDocument();

        for (String term : document.getAllTerms()) {

          double termWeight = clusterDataSet.getTermWeight(document.getId(), term);
          queue.offer(new TermEntry(term, termWeight * getDocumentCount(term, cluster)));
        }
      }

      String label = "";
      StringBuilder labelBuilder = new StringBuilder();

      TreeSet<String> words = Sets.newTreeSet();

      // TODO this is a debug version of labels
      for (TermEntry termEntry : queue) {
        labelBuilder.append(termEntry.getTerm()).append(":").append(String.format("%7.5f", termEntry.getScore()))
            .append(";").append(getDocumentCount(termEntry.getTerm(), cluster)).append(",");

        words.add(termEntry.getTerm());
      }

      if (labelBuilder.length() > 0) {
        label = labelBuilder.substring(0, labelBuilder.length() - 1);
      }
      cluster.setLabel(words.toString());
    }
  }

  private static int getDocumentCount(String term, Cluster<DocumentDataElement> cluster) {
    int count = 0;

    for (DocumentDataElement elem : cluster.getDataElements()) {
      if (elem.getDocument().getTermCount(term) > 0) {
        count++;
      }
    }

    return count;
  }

  private static class TermEntry {
    private String _term;
    private double _score;

    private TermEntry(String term, double weight) {
      _term = term;
      _score = weight;
    }

    private double getScore() {
      return _score;
    }

    private String getTerm() {
      return _term;
    }
  }

}
