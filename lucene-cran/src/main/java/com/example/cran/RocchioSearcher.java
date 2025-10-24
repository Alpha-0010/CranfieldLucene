package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RocchioSearcher {
    private final Path indexPath;
    private final float alpha;      // weight for original query
    private final float beta;       // weight for expansion terms
    private final int fbDocs;       // feedback docs (top-N)
    private final int maxExpTerms;  // expansion terms to add

    public RocchioSearcher(Path indexPath, float alpha, float beta, int fbDocs, int maxExpTerms) {
        this.indexPath = indexPath;
        this.alpha = alpha;
        this.beta = beta;
        this.fbDocs = fbDocs;
        this.maxExpTerms = maxExpTerms;
    }

    public void search(String queriesFile, String outputFile) throws Exception {
        Analyzer analyzer = new EnglishAnalyzer();

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            // Use the tuned BM25 we liked; adjust if you want
            searcher.setSimilarity(new BM25Similarity(1.5f, 0.6f));

            QueryParser parser = new QueryParser("content", analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);

            Map<Integer, String> queries = parseCranQueries(queriesFile);
            int N = reader.maxDoc();

            for (Map.Entry<Integer, String> e : queries.entrySet()) {
                int qid = e.getKey();
                String qtext = e.getValue();
                if (qtext == null || qtext.isEmpty()) continue;

                // Base query
                Query baseQ = parser.parse(QueryParser.escape(qtext));

                // First pass: get feedback docs
                TopDocs fb = searcher.search(baseQ, Math.max(fbDocs, 1));

                // Collect terms from feedback docs (title + content)
                Map<String, Double> tfMap = new HashMap<>();
                for (ScoreDoc sd : fb.scoreDocs) {
                    Document d = searcher.doc(sd.doc);
                    StringBuilder sb = new StringBuilder();
                    String title = d.get("title");
                    String content = d.get("content");
                    if (title != null) sb.append(title).append(' ');
                    if (content != null) sb.append(content);
                    for (String tok : analyzeText(analyzer, "content", sb.toString())) {
                        if (tok.length() < 3) continue; // discard tiny tokens
                        tfMap.merge(tok, 1.0, Double::sum);
                    }
                }

                // Compute TF-IDF weights
                double maxWeight = 0.0;
                Map<String, Double> tfidf = new HashMap<>();
                for (Map.Entry<String, Double> te : tfMap.entrySet()) {
                    String term = te.getKey();
                    int df = reader.docFreq(new Term("content", term));
                    if (df <= 0) continue;
                    double idf = Math.log((N + 1.0) / (df + 1.0)) + 1.0; // classic idf
                    double w = te.getValue() * idf;
                    tfidf.put(term, w);
                    if (w > maxWeight) maxWeight = w;
                }

                // Original query tokens to avoid duplicating
                Set<String> originalTokens = new HashSet<>(analyzeText(analyzer, "content", qtext));

                // Pick top expansion terms not already in query
                List<Map.Entry<String, Double>> topExp = tfidf.entrySet().stream()
                        .filter(en -> !originalTokens.contains(en.getKey()))
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(maxExpTerms)
                        .collect(Collectors.toList());

                // Build expanded query: alpha * base + sum beta * termQuery
                BooleanQuery.Builder expanded = new BooleanQuery.Builder();
                expanded.add(new BoostQuery(baseQ, alpha), BooleanClause.Occur.SHOULD);

                for (Map.Entry<String, Double> en : topExp) {
                    double norm = maxWeight > 0 ? (en.getValue() / maxWeight) : 0.0;
                    float boost = (float) (beta * (0.5 + 0.5 * norm)); // scaled into [0.5*beta, 1*beta]
                    Query tq = new BoostQuery(new TermQuery(new Term("content", en.getKey())), boost);
                    expanded.add(tq, BooleanClause.Occur.SHOULD);
                }

                TopDocs finalResults = searcher.search(expanded.build(), 1000);
                ScoreDoc[] hits = finalResults.scoreDocs;

                for (int i = 0; i < hits.length; i++) {
                    Document doc = searcher.doc(hits[i].doc);
                    String docno = doc.get("id");
                    float score = hits[i].score;
                    int rank = i + 1;
                    bw.write(qid + " Q0 " + docno + " " + rank + " " + score + " run_rocchio");
                    bw.newLine();
                }
            }

            bw.flush();
        }
    }

    private static List<String> analyzeText(Analyzer analyzer, String field, String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        try (var ts = analyzer.tokenStream(field, text)) {
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(termAtt.toString());
            }
            ts.end();
        }
        return tokens;
    }

    // same parser as others
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
