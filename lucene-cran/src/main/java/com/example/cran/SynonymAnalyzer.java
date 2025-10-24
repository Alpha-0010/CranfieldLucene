package com.example.cran;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;

public class SynonymAnalyzer extends Analyzer {

    private static final SynonymMap synonymMap;

    static {
        try {
            SynonymMap.Builder builder = new SynonymMap.Builder(true);

            // ✨ Add domain-specific synonyms
            builder.add(new CharsRef("airplane"), new CharsRef("aircraft"), true);
            builder.add(new CharsRef("aeroplane"), new CharsRef("aircraft"), true);
            builder.add(new CharsRef("lift"), new CharsRef("aerodynamic"), true);
            builder.add(new CharsRef("jet"), new CharsRef("aircraft"), true);
            builder.add(new CharsRef("engine"), new CharsRef("propulsion"), true);
            builder.add(new CharsRef("rocket"), new CharsRef("missile"), true);
            builder.add(new CharsRef("wing"), new CharsRef("airfoil"), true);

            synonymMap = builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build synonym map", e);
        }
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        StandardTokenizer tokenizer = new StandardTokenizer();
        TokenStream filter = new EnglishPossessiveFilter(tokenizer);
        filter = new LowerCaseFilter(filter);
        filter = new SynonymGraphFilter(filter, synonymMap, true);
        return new TokenStreamComponents(tokenizer, filter);
    }
}
