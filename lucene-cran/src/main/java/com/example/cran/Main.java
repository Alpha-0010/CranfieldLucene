package com.example.cran;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {

        // 📁 Paths
        Path indexPath = Paths.get("target/index_cran");
        String cranFilePath = "src/main/resources/cran/cran.all.1400";
        String queriesFile = "src/main/resources/cran/cran.qry";
        String qrelsFile = "src/main/resources/cran/cranqrel";
        String resultsDir = "src/main/results";

        String outputFileEnglish = "target/cran_results_english.txt";
        String outputFileNGram = "target/cran_results_ngram.txt";
        String outputFileSynonym = "target/cran_results_synonym.txt";

        // Indexing
        CranfieldParser parser = new CranfieldParser(Paths.get("src/main/resources/cran").toFile());
        List<CranfieldParser.CranDoc> docs = parser.parseDocs();
        Indexer indexer = new Indexer(indexPath);
        indexer.index(docs);
        System.out.println(" Indexing completed. Index stored at: " + indexPath);

        // -----------------------------------------------------------
        // Baseline - EnglishAnalyzer
        // -----------------------------------------------------------
        Searcher baselineSearcher = new Searcher(indexPath);
        baselineSearcher.search(queriesFile, outputFileEnglish, "english"); // english, standard, whitespace.
        System.out.println("\n🔸 Evaluating Baseline English Analyzer...");
        runTrecEval(qrelsFile, outputFileEnglish, resultsDir, "english");

        // -----------------------------------------------------------
        // N-Gram Analyzer
        // -----------------------------------------------------------
        NGramSearcher ngramSearcher = new NGramSearcher(indexPath);
        ngramSearcher.search(queriesFile, outputFileNGram);
        System.out.println("\n🔸 Evaluating N-Gram Analyzer...");
        runTrecEval(qrelsFile, outputFileNGram, resultsDir, "ngram");

        // -----------------------------------------------------------
        // Synonym Analyzer
        // -----------------------------------------------------------
        SynonymSearcher synonymSearcher = new SynonymSearcher(indexPath);
        synonymSearcher.search(queriesFile, outputFileSynonym);
        System.out.println("\n🔸 Evaluating Synonym Analyzer...");
        runTrecEval(qrelsFile, outputFileSynonym, resultsDir, "synonym");

        // -----------------------------------------------------------
        // BM25 Parameter Tuning
        // -----------------------------------------------------------
        float[] k1Values = {0.8f, 1.2f, 1.5f, 2.0f};
        float[] bValues = {0.4f, 0.6f, 0.75f};

        for (float k1 : k1Values) {
            for (float b : bValues) {
                String outputFileBM25 = "target/cran_results_bm25_" + k1 + "_" + b + ".txt";
                BM25TunedSearcher tunedSearcher = new BM25TunedSearcher(indexPath, k1, b);
                tunedSearcher.search(queriesFile, outputFileBM25);
                System.out.println("\n🔸 Evaluating BM25 tuned (k1=" + k1 + ", b=" + b + ")...");
                runTrecEval(qrelsFile, outputFileBM25, resultsDir, "bm25_" + k1 + "_" + b);
            }
        }

        // -----------------------------------------------------------
        // Field Boosting (Title vs Body)
        // -----------------------------------------------------------
        float[] titleBoosts = {1.5f, 2.0f, 3.0f};
        float[] bodyBoosts = {1.0f};

        for (float tBoost : titleBoosts) {
            for (float bBoost : bodyBoosts) {
                String outputFileBoost = "target/cran_results_boost_t" + tBoost + "_b" + bBoost + ".txt";
                BoostedFieldSearcher boostedSearcher = new BoostedFieldSearcher(indexPath, tBoost, bBoost);
                boostedSearcher.search(queriesFile, outputFileBoost);
                System.out.println("\n🔸 Evaluating Field Boosting (title=" + tBoost + ", body=" + bBoost + ")...");
                runTrecEval(qrelsFile, outputFileBoost, resultsDir, "boost_t" + tBoost + "_b" + bBoost);
            }
        }

        // -----------------------------------------------------------
        // Rocchio PRF (Pseudo Relevance Feedback)
        // -----------------------------------------------------------
        float[] alphas = {1.0f};
        float[] betas  = {0.5f, 0.75f};
        int[] fbDocs   = {5, 10};
        int[] expTerms = {10, 15};

        for (float a : alphas) {
            for (float b : betas) {
                for (int d : fbDocs) {
                    for (int t : expTerms) {
                        String tag = "rocchio_a" + a + "_b" + b + "_d" + d + "_t" + t;
                        String out = "target/cran_results_" + tag + ".txt";
                        RocchioSearcher roc = new RocchioSearcher(indexPath, a, b, d, t);
                        roc.search(queriesFile, out);
                        System.out.println("\n🔸 Evaluating Rocchio (" + tag + ")...");
                        runTrecEval(qrelsFile, out, resultsDir, tag);
                    }
                }
            }
        }

        // -----------------------------------------------------------
        // Title-based Reranking
        // -----------------------------------------------------------
        float[] rerankBoosts = {0.5f, 1.0f, 2.0f};
        int[] rerankTops = {50, 100};

        for (float boost : rerankBoosts) {
            for (int topN : rerankTops) {
                String outFile = "target/cran_results_rerank_boost" + boost + "_top" + topN + ".txt";
                RerankTitleBoostSearcher reranker = new RerankTitleBoostSearcher(indexPath, boost, topN);
                reranker.search(queriesFile, outFile);
                System.out.println("\n🔸 Evaluating Title Reranking (boost=" + boost + ", topN=" + topN + ")...");
                runTrecEval(qrelsFile, outFile, resultsDir, "rerank_b" + boost + "_t" + topN);
            }
        }

        System.out.println("\nAll evaluations completed. Results saved in: " + resultsDir);
    }

    /**
     * Runs trec_eval on the given results file and saves metrics.
     */
    private static void runTrecEval(String qrelsFile, String resultsFile, String resultsDir, String tag) throws IOException, InterruptedException {
        File dir = new File(resultsDir);
        if (!dir.exists()) dir.mkdirs();

        String metricsFile = resultsDir + "/metrics_" + tag + ".txt";

        ProcessBuilder pb = new ProcessBuilder(
                "./trec_eval",
                "-m", "map",
                "-m", "gm_map",
                "-m", "P.5",
                "-m", "P.10",
                "-m", "recall.1000",
                new File(qrelsFile).getAbsolutePath(),
                new File(resultsFile).getAbsolutePath()
        );

        pb.directory(new File("trec_eval"));
        pb.redirectErrorStream(true);

        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new FileWriter(metricsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                writer.write(line);
                writer.newLine();
            }
        }

        int exitCode = p.waitFor();
        if (exitCode == 0)
            System.out.println("Metrics saved to " + metricsFile);
        else
            System.err.println("trec_eval failed for " + tag);
    }
}