package com.example.cran;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {

        // 🛣️ Paths
        Path indexPath = Paths.get("target/index_cran");
        String cranFilePath = "src/main/resources/cran/cran.all.1400";
        String queriesFile = "src/main/resources/cran/cran.qry";
        String outputFile = "target/cran_results.txt";

        // 📝 Analyzer choice (same as your friend's setup)
        String analyzerName = "english";  // or "standard", "whitespace"

        // Parse Cranfield collection
        CranfieldParser parser = new CranfieldParser(Paths.get("src/main/resources/cran").toFile());
        List<CranfieldParser.CranDoc> docs = parser.parseDocs();

        // Index documents
        Indexer indexer = new Indexer(indexPath);
        indexer.index(docs);
        System.out.println("Indexing completed. Index stored at: " + indexPath);

        // Search queries and write results in TREC format
        Searcher searcher = new Searcher(indexPath);
        searcher.search(queriesFile, outputFile, analyzerName);
        System.out.println("Search completed. Results written to: " + outputFile);

        // Evaluate with trec_eval
        // ./trec_eval ../src/main/resources/cran/cranqrel ../target/cran_results.txt | tee ../target/trec_eval.txt
    }
}