package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public class CustomAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        if ("title".equals(fieldName)) {
            // N-gram tokenizer for title field (3 to 5 grams)
            NGramTokenizer tokenizer = new NGramTokenizer(3, 5);
            TokenStream filter = new LowerCaseFilter(tokenizer);
            return new TokenStreamComponents(tokenizer, filter);
        } else {
            // English style for everything else
            StandardTokenizer tokenizer = new StandardTokenizer();
            TokenStream filter = new EnglishPossessiveFilter(tokenizer);
            filter = new LowerCaseFilter(filter);
            return new TokenStreamComponents(tokenizer, filter);
        }
    }
}