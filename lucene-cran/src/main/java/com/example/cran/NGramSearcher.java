package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class NGramSearcher {
    private final Path indexPath;

    public NGramSearcher(Path indexPath) {
        this.indexPath = indexPath;
    }

    public void search(String queriesFile, String outputFile) throws Exception {
        Analyzer analyzer = new CustomAnalyzer();

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", analyzer);

            Map<Integer, String> queries = parseCranQueries(queriesFile);

            for (Map.Entry<Integer, String> e : queries.entrySet()) {
                int qid = e.getKey();
                String qtext = e.getValue();
                if (qtext == null || qtext.isEmpty()) continue;

                Query q = parser.parse(QueryParser.escape(qtext));
                TopDocs top = searcher.search(q, 1000);  // more results
                ScoreDoc[] hits = top.scoreDocs;

                for (int i = 0; i < hits.length; i++) {
                    Document doc = searcher.doc(hits[i].doc);
                    String docno = doc.get("id");
                    float score = hits[i].score;
                    int rank = i + 1;
                    bw.write(qid + " Q0 " + docno + " " + rank + " " + score + " run_ngram");
                    bw.newLine();
                }
            }

            bw.flush();
        }
    }

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