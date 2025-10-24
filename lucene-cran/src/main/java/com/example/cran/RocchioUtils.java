package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.util.*;

public class RocchioUtils {

    public static String expandQuery(IndexSearcher searcher, Analyzer analyzer,
                                     String field, String originalQuery,
                                     float alpha, float beta,
                                     int fbDocs, int expTerms) throws Exception {

        // original query as base
        QueryParser parser = new QueryParser(field, analyzer);
        Query q = parser.parse(QueryParser.escape(originalQuery));

        TopDocs top = searcher.search(q, fbDocs);
        if (top.scoreDocs.length == 0) {
            return originalQuery;
        }

        // collect term frequencies from top docs
        Map<String, Float> termWeights = new HashMap<>();
        ClassicSimilarity sim = new ClassicSimilarity();

        for (ScoreDoc sd : top.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            String content = doc.get(field);
            if (content == null) continue;

            String[] terms = content.split("\\s+");
            Map<String, Integer> tf = new HashMap<>();
            for (String t : terms) {
                if (t.isEmpty()) continue;
                tf.put(t, tf.getOrDefault(t, 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : tf.entrySet()) {
                float weight = beta * entry.getValue();
                termWeights.put(entry.getKey(), termWeights.getOrDefault(entry.getKey(), 0f) + weight);
            }
        }

        // sort and pick top expansion terms
        List<Map.Entry<String, Float>> sorted = new ArrayList<>(termWeights.entrySet());
        sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        StringBuilder expanded = new StringBuilder(originalQuery);
        int count = 0;
        for (Map.Entry<String, Float> e : sorted) {
            if (count >= expTerms) break;
            expanded.append(" ").append(e.getKey());
            count++;
        }

        return expanded.toString();
    }
}
