package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.util.List;

public class Indexer {
    private final Path indexPath;

    public Indexer(Path indexPath) {
        this.indexPath = indexPath;
    }

    public void index(List<CranfieldParser.CranDoc> docs) throws Exception {
        Directory dir = FSDirectory.open(indexPath);
        Analyzer analyzer = new EnglishAnalyzer(); 

        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter w = new IndexWriter(dir, cfg)) {
            for (CranfieldParser.CranDoc d : docs) {
                Document luc = new Document();

                luc.add(new StringField("id", d.docno, Field.Store.YES));
                luc.add(new TextField("title", d.title, Field.Store.YES));
                luc.add(new TextField("author", d.author, Field.Store.YES));
                luc.add(new TextField("content", d.body, Field.Store.YES));

                w.addDocument(luc);
            }
            w.commit();
        }
    }
}