package com.example.cran;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class CranfieldParser {
    public static class CranDoc {
        public String docno;
        public String title = "";
        public String author = "";
        public String biblio = "";
        public String body = "";
    }

    private final File dataDir;

    public CranfieldParser(File dataDir) {
        this.dataDir = dataDir;
    }

    public List<CranDoc> parseDocs() throws IOException {
        File f = new File(dataDir, "cran.all.1400");
        if (!f.exists()) throw new FileNotFoundException("Missing cran.all.1400 in " + dataDir.getAbsolutePath());
        String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        // Cranfield format: .I <id>, .T title, .A author, .B biblio, .W body
        List<CranDoc> docs = new ArrayList<>();
        String[] blocks = text.split("\\n\\.I\\s+"); // split on .I (doc id)
        for (String block : blocks) {
            String b = block.trim();
            if (b.isEmpty()) continue;
            // First line is the id
            int nl = b.indexOf('\n');
            String id = (nl == -1) ? b : b.substring(0, nl).trim();
            String rest = (nl == -1) ? "" : b.substring(nl + 1);

            CranDoc d = new CranDoc();
            d.docno = id;

            // Extract fields by section markers
            d.title = extractSection(rest, ".T", ".A");
            d.author = extractSection(rest, ".A", ".B");
            d.biblio = extractSection(rest, ".B", ".W");
            d.body = extractSection(rest, ".W", null);

            docs.add(d);
        }
        return docs;
    }

    private static String extractSection(String text, String startMarker, String nextMarkerOrNull) {
        int s = text.indexOf(startMarker);
        if (s < 0) return "";
        s = s + startMarker.length();
        int e = (nextMarkerOrNull == null) ? -1 : text.indexOf(nextMarkerOrNull, s);
        String chunk = (e < 0) ? text.substring(s) : text.substring(s, e);
        return chunk.replaceAll("^\\s+|\\s+$", "").replace("\r", "");
    }

    /** Returns queries as map: qid -> text (from cran.qry) */
    public Map<Integer, String> parseQueries() throws IOException {
        File f = new File(dataDir, "cran.qry");
        if (!f.exists()) throw new FileNotFoundException("Missing cran.qry in " + dataDir.getAbsolutePath());
        String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        Map<Integer, String> queries = new LinkedHashMap<>();

        // Queries are similar to docs: .I <id> followed by .W and the text
        String[] blocks = text.split("\\n\\.I\\s+");
        Pattern pId = Pattern.compile("^(\\d+)");
        for (String block : blocks) {
            String b = block.trim();
            if (b.isEmpty()) continue;
            BufferedReader br = new BufferedReader(new StringReader(b));
            String first = br.readLine();
            if (first == null) continue;
            Matcher m = pId.matcher(first.trim());
            if (!m.find()) continue;
            int qid = Integer.parseInt(m.group(1));
            String rest = b.substring(first.length());
            String qtext = sectionAfter(rest, ".W");
            qtext = qtext.replaceAll("\\s+", " ").trim();
            queries.put(qid, qtext);
        }
        return queries;
    }

    private static String sectionAfter(String text, String marker) {
        int s = text.indexOf(marker);
        if (s < 0) return text.trim();
        s += marker.length();
        return text.substring(s).trim();
    }

    /** Returns qrels lines as: qid 0 docno rel */
    public List<String> loadQrels() throws IOException {
        // File can be named "qrels" or "cranqrel"
        File qrels = new File(dataDir, "qrels");
        if (!qrels.exists()) qrels = new File(dataDir, "cranqrel");
        if (!qrels.exists()) throw new FileNotFoundException("Missing qrels/cranqrel in " + dataDir.getAbsolutePath());

        List<String> lines = java.nio.file.Files.readAllLines(qrels.toPath(), StandardCharsets.UTF_8);
        // Normalize to "qid 0 docno rel"
        List<String> out = new ArrayList<>();
        for (String ln : lines) {
            String s = ln.trim();
            if (s.isEmpty()) continue;
            String[] parts = s.split("\\s+");
            // Many cran qrels are: qid  docno  rel
            // Ensure 4 columns: qid 0 docno rel
            if (parts.length == 3) {
                out.add(parts[0] + " 0 " + parts[1] + " " + parts[2]);
            } else if (parts.length >= 4) {
                out.add(parts[0] + " 0 " + parts[2] + " " + parts[3]);
            }
        }
        return out;
    }
}