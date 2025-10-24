package com.example.cran;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {

        // 🛣️ Paths
        Path indexPath = Paths.get("target/index_cran");
        String cranFilePath = "src/main/resources/cran/cran.all.1400";
        String queriesFile = "src/main/resources/cran/cran.qry";
        String outputFile = "target/cran_results.txt";
        String qrelsFile = "src/main/resources/cran/cranqrel";
        String resultsDir = "src/main/results";

        // 📝 Analyzer choice
        String analyzerName = "english";  // "standard", "whitespace" or "english"

        // 1. Parse Cranfield collection
        CranfieldParser parser = new CranfieldParser(Paths.get("src/main/resources/cran").toFile());
        List<CranfieldParser.CranDoc> docs = parser.parseDocs();

        // 2. Index documents
        Indexer indexer = new Indexer(indexPath);
        indexer.index(docs);
        System.out.println("Indexing completed. Index stored at: " + indexPath);

        // 3. Search
        Searcher searcher = new Searcher(indexPath);
        searcher.search(queriesFile, outputFile, analyzerName);
        System.out.println("Search completed. Results written to: " + outputFile);

        // 4. Evaluate with trec_eval (automated)
        runTrecEval(qrelsFile, outputFile, resultsDir, analyzerName);
    }

    private static void runTrecEval(String qrelsFile, String resultsFile, String resultsDir, String analyzer) throws IOException, InterruptedException {
        // Ensure results directory exists
        File dir = new File(resultsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String metricsFile = resultsDir + "/metrics_" + analyzer + ".txt";

        // Use absolute paths to avoid path resolution issues
        String absQrels = new File(qrelsFile).getAbsolutePath();
        String absResults = new File(resultsFile).getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
                "./trec_eval",
                "-m", "map",
                "-m", "gm_map",
                "-m", "P.5",
                "-m", "recall.1000",
                absQrels,
                absResults
        );

        pb.directory(new File("trec_eval"));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new FileWriter(metricsFile))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);      // prints on screen
                writer.write(line);            // saves to the file
                writer.newLine();
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            System.out.println("Evaluation completed. Metrics saved to: " + metricsFile);
        } else {
            System.err.println("trec_eval failed. Exit code: " + exitCode);
        }
    }
}