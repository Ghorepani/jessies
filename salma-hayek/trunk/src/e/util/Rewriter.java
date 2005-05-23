package e.util;

import java.util.regex.*;

/**
 * A rewriter does a global substitution in the strings passed to its
 * 'rewrite' method. It uses the pattern supplied to its constructor,
 * and is like 'String.replaceAll' except for the fact that its
 * replacement strings are generated by invoking a method you write,
 * rather than from another string.
 *
 * This class is supposed to be equivalent to Ruby's 'gsub' when given
 * a block. This is the nicest syntax I've managed to come up with in
 * Java so far. It's not too bad, and might actually be preferable if
 * you want to do the same rewriting to a number of strings in the same
 * method or class.
 *
 * See the example 'main' for a sample of how to use this class.
 *
 * @author Elliott Hughes
 */
public abstract class Rewriter {
    private Pattern pattern;
    private Matcher matcher;

    /**
     * Constructs a rewriter using the given regular expression;
     * the syntax is the same as for 'Pattern.compile'.
     */
    public Rewriter(String regularExpression) {
        this.pattern = Pattern.compile(regularExpression);
    }

    /**
     * Returns the input subsequence captured by the given group
     * during the previous match operation.
     */
    public String group(int i) {
        return matcher.group(i);
    }

    /**
     * Overridden to compute a replacement for each match. Use
     * the method 'group' to access the captured groups.
     */
    public abstract String replacement();

    /**
     * Returns the result of rewriting 'original' by invoking
     * the method 'replacement' for each match of the regular
     * expression supplied to the constructor.
     */
    public String rewrite(CharSequence original) {
        this.matcher = pattern.matcher(original);
        StringBuffer result = new StringBuffer(original.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, replacement());
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static void main(String[] arguments) {
        // Rewrite an ancient unit of length in SI units.
        String result = new Rewriter("([0-9]+(\\.[0-9]+)?)[- ]?(inch(es)?)") {
            public String replacement() {
                float inches = Float.parseFloat(group(1));
                return Float.toString(2.54f * inches) + " cm";
            }
        }.rewrite("a 17 inch display");
        System.out.println(result);
        
        // The "Searching and Replacing with Non-Constant Values Using a
        // Regular Expression" example from the Java Almanac.
        result = new Rewriter("([a-zA-Z]+[0-9]+)") {
            public String replacement() {
                return group(1).toUpperCase();
            }
        }.rewrite("ab12 cd efg34");
        System.out.println(result);
        
        // Rewrite durations in milliseconds in ISO 8601 format.
        Rewriter rewriter = new Rewriter("(\\d+)\\s*ms") {
            public String replacement() {
                long milliseconds = Long.parseLong(group(1));
                return TimeUtilities.durationToIsoString(milliseconds);
            }
        };
        for (String argument : arguments) {
            System.err.println(rewriter.rewrite(argument));
        }
    }
}
