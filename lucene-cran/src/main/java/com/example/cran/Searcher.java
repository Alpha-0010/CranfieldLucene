package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Searcher {
    private final Path indexPath;

    public Searcher(Path indexPath) {
        this.indexPath = indexPath;
    }

    /**
     * Executes Cranfield search with TREC-format output.
     * @param queriesFile path to cran.qry
     * @param outputFile path to write results
     * @param analyzerName "english" | "standard" | "whitespace"
     */
    public void search(String queriesFile, String outputFile, String analyzerName) throws Exception {
        Analyzer analyzer = getAnalyzer(analyzerName.toLowerCase());
        Directory dir = FSDirectory.open(indexPath);
        DirectoryReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        // Parse queries from Cranfield .qry file
        Map<Integer, String> queries = parseCranQueries(queriesFile);
        QueryParser parser = new QueryParser("content", analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
            for (Map.Entry<Integer, String> e : queries.entrySet()) {
                int qid = e.getKey();
                String qtext = e.getValue();
                if (qtext == null || qtext.isEmpty()) continue;

                Query q = parser.parse(QueryParser.escape(qtext));
                TopDocs top = searcher.search(q, 1000);
                ScoreDoc[] hits = top.scoreDocs;

                for (int i = 0; i < hits.length; i++) {
                    Document doc = searcher.doc(hits[i].doc);
                    String docno = doc.get("id");
                    float score = hits[i].score;
                    int rank = i + 1;
                    // TREC format: qid Q0 docno rank score runid
                    bw.write(qid + " Q0 " + docno + " " + rank + " " + score + " run_" + analyzerName + "\n");
                }
            }
            bw.flush();
        }

        reader.close();
        dir.close();
    }

    // get analyzer based on name
    private static Analyzer getAnalyzer(String name) {
        switch (name) {
            case "english": return new EnglishAnalyzer();
            case "whitespace": return new WhitespaceAnalyzer();
            default: return new StandardAnalyzer();
        }
    }

    // parse Cranfield queries from .qry file
    private static Map<Integer, String> parseCranQueries(String queriesFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(queriesFile));
        Map<Integer, String> map = new LinkedHashMap<>();
        String line;
        int currentId = 0;
        StringBuilder sb = null;
        boolean inW = false;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith(".I")) {
                if (currentId != 0 && sb != null) {
                    map.put(currentId, sb.toString().trim());
                }
                currentId++;
                sb = new StringBuilder();
                inW = false;
            } else if (line.startsWith(".W")) {
                inW = true;
            } else if (inW && !line.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(line);
            }
        }

        if (currentId != 0 && sb != null) {
            map.put(currentId, sb.toString().trim());
        }

        br.close();
        return map;
    }
}
