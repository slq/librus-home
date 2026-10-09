package pl.librushome.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchTextTest {
    @Test public void polishLettersCaseAndCombiningMarksMatch() {
        assertEquals("zazolc gesla jazn",SearchText.normalize("  ZAŻÓŁĆ GĘŚLĄ JAŹŃ  "));
        assertTrue(SearchText.matches(SearchText.words("ulamkow zolc"),"Żółć · Ćwiczenia z ułamków"));
        assertEquals(SearchText.normalize("ó"),SearchText.normalize("o\u0301"));
    }
    @Test public void everyWordIsRequiredRegardlessOfOrderAndWhitespace() {
        String[] words=SearchText.words("  Nowak\n matematyka\t");
        assertEquals(2,words.length);
        assertTrue(SearchText.matches(words,"Matematyka · Anna Nowak"));
        assertFalse(SearchText.matches(words,"Matematyka · Anna Kowalska"));
    }
    @Test public void punctuationIsLiteralAndNotARegex() {
        assertTrue(SearchText.matches(SearchText.words("[A]*"),"Ogłoszenie [A]*"));
        assertFalse(SearchText.matches(SearchText.words(".*"),"dowolny temat"));
        assertTrue(SearchText.matches(SearchText.words("07.10.2026"),"Termin: 07.10.2026"));
    }
    @Test public void blankAndNullQueriesHaveNoWords() {
        assertEquals(0,SearchText.words(null).length);
        assertEquals(0,SearchText.words(" \n\t ").length);
    }
}
