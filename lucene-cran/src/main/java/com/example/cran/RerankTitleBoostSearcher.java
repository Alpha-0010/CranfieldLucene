package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uses Rocchio expansion for base retrieval
 * and boosts title score in reranking.
 */
public class RerankTitleBoostSearcher {

    private final Path indexPath;
    private final float rerankBoost;
    private final int topN;
    private final Analyzer analyzer;

    // Rocchio hyperparameters
    private final float alpha = 1.0f;
    private final float beta = 0.75f;
    private final int fbDocs = 10;
    private final int expTerms = 15;

    public RerankTitleBoostSearcher(Path indexPath, float rerankBoost, int topN) {
        this.indexPath = indexPath;
        this.rerankBoost = rerankBoost;
        this.topN = topN;
        this.analyzer = new EnglishAnalyzer();
    }

    public void search(String queriesFile, String outputFile) throws Exception {
        Directory dir = FSDirectory.open(indexPath);
        DirectoryReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        // Use same field used in baseline Searcher ("all" in your case)
        QueryParser bodyParser = new QueryParser("content", analyzer);
        QueryParser titleParser = new QueryParser("title", analyzer);

        Map<Integer, String> queries = parseCranQueries(queriesFile);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {

            for (Map.Entry<Integer, String> e : queries.entrySet()) {
                int qid = e.getKey();
                String qtext = e.getValue();
                if (qtext == null || qtext.isEmpty()) continue;

                // Rocchio expansion
                String expandedQuery = RocchioUtils.expandQuery(searcher, analyzer, "all",
                        qtext, alpha, beta, fbDocs, expTerms);

                // Base retrieval on expanded query
                Query baseQ = bodyParser.parse(QueryParser.escape(expandedQuery));
                TopDocs top = searcher.search(baseQ, topN);

                if (top.scoreDocs.length == 0) {
                    // fallback to original query if Rocchio fails
                    baseQ = bodyParser.parse(QueryParser.escape(qtext));
                    top = searcher.search(baseQ, topN);
                }

                System.out.println("Query " + qid + " got " + top.scoreDocs.length + " docs for reranking.");

                // Build title query for reranking
                Query titleQ = titleParser.parse(QueryParser.escape(qtext));

                // STEP 4 — Rerank based on title match
                ScoreDoc[] reranked = new ScoreDoc[top.scoreDocs.length];
                for (int i = 0; i < top.scoreDocs.length; i++) {
                    ScoreDoc sd = top.scoreDocs[i];
                    Number titleVal = searcher.explain(titleQ, sd.doc).getValue();
                    float titleScore = titleVal != null ? titleVal.floatValue() : 0f;
                    float newScore = sd.score + rerankBoost * titleScore;
                    reranked[i] = new ScoreDoc(sd.doc, newScore);
                }

                // Sort by newScore descending
                reranked = sortScoreDocs(reranked);

                // Write to TREC format file
                for (int rank = 0; rank < reranked.length; rank++) {
                    Document doc = searcher.doc(reranked[rank].doc);
                    String docno = doc.get("id");
                    bw.write(qid + " Q0 " + docno + " " + (rank + 1) + " " + reranked[rank].score + " run_rerank_rocchio\n");
                }
            }

            bw.flush();
        }

        reader.close();
        dir.close();
    }

    // helper: stable sort of ScoreDocs by score descending
    private static ScoreDoc[] sortScoreDocs(ScoreDoc[] sds) {
        java.util.Arrays.sort(sds, (a, b) -> Float.compare(b.score, a.score));
        return sds;
    }

    // parse Cranfield queries (same as in Searcher.java)
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