package terminatorn;

import java.awt.*;
import java.awt.event.*;
import java.util.regex.*;

/**

@author Phil Norman
*/

public class HyperlinkHighlighter implements Highlighter {
	/** The underlined blue standard hyperlink style. */
	private Style style = new Style(Color.blue, null, null, Boolean.TRUE);

	/** Pattern for matching hyperlinks. */
	private Pattern addressPattern = Pattern.compile("\\b[a-zA-Z]+:/*[\\w\\.]+(:\\d+)?[/\\w\\.\\?&=\\+]*");

	public String getName() {
		return "Hyperlink Highlighter";
	}
	
	/** Request to add highlights to all lines of the view from the index given onwards. */
	public void addHighlights(JTextBuffer view, int firstLineIndex) {
		TextBuffer model = view.getModel();
		for (int i = firstLineIndex; i < model.getLineCount(); i++) {
			String line = model.getLine(i);
			if (line.indexOf(':') != -1) {  // Trivial optimisation - just like us, all hyperlinks have a colon.
				addHighlights(view, i, line);
			}
		}
	}
	
	private void addHighlights(JTextBuffer view, int lineIndex, String line) {
		Matcher address = addressPattern.matcher(line);
		while (address.find()) {
			Highlight light = new Highlight(this, new Location(lineIndex, address.start()),
					new Location(lineIndex, address.end()), style);
			light.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			view.addHighlight(light);
		}
	}

	/** Request to do something when the user clicks on a Highlight generated by this Highlighter. */
	public void highlightClicked(JTextBuffer view, Highlight highlight, String highlitText, MouseEvent event) {
		Log.warn("Click on hyperlink " + highlitText);
	}
}