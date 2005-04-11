package e.ptextarea;

import java.awt.*;
import java.awt.event.*;
import e.util.*;

public class PKeyHandler extends KeyAdapter {
    private PTextArea textArea;
    private UpDownMovementHandler movementHandler = new UpDownMovementHandler();
    
    public PKeyHandler(PTextArea textArea) {
        this.textArea = textArea;
        textArea.addCaretListener(movementHandler);
    }
    
    public void keyPressed(KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getKeyCode()) {
            case KeyEvent.VK_T:
                textArea.printLineInfo();
                return;
            case KeyEvent.VK_L:
                textArea.getLineList().printLineInfo();
                return;
            case KeyEvent.VK_R:
                textArea.repaint();
                return;
                
            case KeyEvent.VK_X:
                textArea.cut();
                return;
            case KeyEvent.VK_C:
                textArea.copy();
                return;
            case KeyEvent.VK_V:
                textArea.paste();
                return;
                
            case KeyEvent.VK_Z:
                undoRedo(event.isShiftDown());
                return;
            }
        }
        if (handleInvisibleKeyPressed(event)) {
            event.consume();
        }
    }
    
    private void undoRedo(boolean isShifted) {
        PUndoBuffer undoer = textArea.getPTextBuffer().getUndoBuffer();
        if (isShifted) {
            undoer.redo();
        } else {
            undoer.undo();
        }
    }
    
    public class PColoredHighlightMatcher implements PHighlightMatcher {
        private Color color;
        
        public PColoredHighlightMatcher(Color color) {
            this.color = color;
        }
        
        public boolean matches(PHighlight highlight) {
            if (highlight instanceof PColoredHighlight) {
                return ((PColoredHighlight) highlight).getColor().equals(color);
            } else {
                return false;
            }
        }
    }
    
    public void keyTyped(KeyEvent event) {
        if (isInsertableCharacter(event)) {
            insertCharacter(event.getKeyChar());
            event.consume();
        }
    }
    
    private boolean isInsertableCharacter(KeyEvent e) {
        if (e.isAltDown() || e.isAltGraphDown() || e.isControlDown() || e.isMetaDown()) {
            return false;
        }
        
        switch (e.getKeyChar()) {
        case KeyEvent.CHAR_UNDEFINED:
        case '\010':  // backspace
        case '\177':  // delete
            return false;
            
        default:
            return true;
        }
    }
    
    private boolean handleInvisibleKeyPressed(KeyEvent event) {
        boolean byWord = GuiUtilities.isMacOs() ? event.isAltDown() : event.isControlDown();
        boolean extendingSelection = event.isShiftDown();
        if (movementHandler.handleMovementKeys(event)) {
            return true;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                moveLeft(byWord, extendingSelection);
                break;
            case KeyEvent.VK_RIGHT:
                moveRight(byWord, extendingSelection);
                break;
            case KeyEvent.VK_HOME: moveCaret(extendingSelection, caretToStartOfLine()); break;
            case KeyEvent.VK_END: moveCaret(extendingSelection, caretToEndOfLine()); break;
            case KeyEvent.VK_BACK_SPACE: backspace(); break;
            case KeyEvent.VK_DELETE: delete(); break;
            
        default:
            return false;
        }
        return true;
    }
    
    private void insertCharacter(char ch) {
        textArea.insert(new CharArrayCharSequence(new char[] { ch }));
    }
    
    private void backspace() {
        int start = textArea.getSelectionStart();
        int end = textArea.getSelectionEnd();
        if (start == end && start > 0) {
            --start;
        }
        if (start != end) {
            textArea.delete(start, end - start);
        }
    }
    
    private void delete() {
        int start = textArea.getSelectionStart();
        int end = textArea.getSelectionEnd();
        if (start == end && end < textArea.getPTextBuffer().length() - 1) {
            ++end;
        }
        if (start != end) {
            textArea.delete(start, end - start);
        }
    }
    
    private void moveCaret(boolean extendingSelection, int newOffset) {
        int start = newOffset;
        int end = newOffset;
        if (extendingSelection) {
            start = Math.min(textArea.getSelectionStart(), start);
            end = Math.max(textArea.getSelectionEnd(), end);
        }
        textArea.select(start, end);
    }
    
    private int caretToStartOfLine() {
        int lineIndex = textArea.getLineOfOffset(textArea.getSelectionStart());
        return textArea.getLineStartOffset(lineIndex);
    }
    
    private int caretToEndOfLine() {
        int lineIndex = textArea.getLineOfOffset(textArea.getSelectionEnd());
        return textArea.getLineEndOffset(lineIndex);
    }
    
    private void moveLeft(boolean byWord, boolean extendingSelection) {
        int newOffset = byWord ? caretToPreviousWord() : caretLeft(extendingSelection);
        moveCaret(extendingSelection, newOffset);
    }
    
    private void moveRight(boolean byWord, boolean extendingSelection) {
        int newOffset = byWord ? caretToNextWord() : caretRight(extendingSelection);
        moveCaret(extendingSelection, newOffset);
    }
    
    private int caretLeft(boolean extendingSelection) {
        // FIXME: we need to remember a bias for keyboard shift+arrow movement.
        if (extendingSelection || textArea.getSelectionStart() == textArea.getSelectionEnd()) {
            return Math.max(0, textArea.getSelectionStart() - 1);
        } else {
            return textArea.getSelectionStart();
        }
    }
    
    private int caretRight(boolean extendingSelection) {
        // FIXME: we need to remember a bias for keyboard shift+arrow movement.
        if (extendingSelection || textArea.getSelectionStart() == textArea.getSelectionEnd()) {
            return Math.min(textArea.getSelectionEnd() + 1, textArea.getPTextBuffer().length());
        } else {
            return textArea.getSelectionEnd();
        }
    }
    
    private int caretToPreviousWord() {
        CharSequence chars = textArea.getPTextBuffer();
        String stopChars = PWordUtilities.DEFAULT_STOP_CHARS;
        int offset = textArea.getSelectionStart();
        
        // If we're at the start of the document, we're not going far.
        if (offset == 0) {
            return 0;
        }
        
        // If we're at the start of a word, go to the start of the word before.
        if (PWordUtilities.isInWord(chars, offset - 1, stopChars) == false) {
            return PWordUtilities.getWordStart(chars, PWordUtilities.getNonWordStart(chars, offset - 1, stopChars), stopChars);
        }
        
        // Otherwise go to the start of the current word.
        return PWordUtilities.getWordStart(chars, offset, stopChars);
    }
    
    private int caretToNextWord() {
        CharSequence chars = textArea.getPTextBuffer();
        String stopChars = PWordUtilities.DEFAULT_STOP_CHARS;
        int offset = textArea.getSelectionEnd();
        
        // If we're at the end of the document, we're not going far.
        if (offset == chars.length()) {
            return offset;
        }
        
        // If we're in a word, go to the end of this word.
        if (PWordUtilities.isInWord(chars, offset, stopChars)) {
            return PWordUtilities.getWordEnd(chars, offset, stopChars);
        }
        
        // Otherwise go to the start of the next word.
        return PWordUtilities.getWordEnd(chars, PWordUtilities.getNonWordEnd(chars, PWordUtilities.getWordEnd(chars, offset, stopChars), stopChars), stopChars);
    }
    
    private class UpDownMovementHandler implements PCaretListener {
        private boolean isEntered = false;
        private int xPixelLocation = -1;
        
        public void caretMoved(int selectionStart, int selectionEnd) {
            if (isEntered == false) {
                xPixelLocation = -1;
            }
        }
        
        public boolean handleMovementKeys(KeyEvent event) {
            isEntered = true;
            if (xPixelLocation == -1) {
                xPixelLocation = getCurrentXPixelLocation();
            }
            boolean extendingSelection = event.isShiftDown();
            try {
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_UP: moveCaret(extendingSelection, caretUp()); return true;
                    case KeyEvent.VK_DOWN: moveCaret(extendingSelection, caretDown()); return true;
                    default: return false;
                }
            } finally {
                isEntered = false;
            }
        }
        
        private int getCurrentXPixelLocation() {
            PCoordinates coords = textArea.getCoordinates(textArea.getSelectionStart());
            return textArea.getViewCoordinates(coords).x;
        }
        
        private int caretUp() {
            PCoordinates coords = textArea.getCoordinates(textArea.getSelectionStart());
            int lineIndex = coords.getLineIndex();
            if (lineIndex == 0) {
                return 0;
            } else {
                int y = textArea.getViewCoordinates(new PCoordinates(lineIndex - 1, 0)).y;
                return textArea.getTextIndex(textArea.getNearestCoordinates(new Point(xPixelLocation, y)));
            }
        }
        
        private int caretDown() {
            PCoordinates coords = textArea.getCoordinates(textArea.getSelectionEnd());
            int lineIndex = coords.getLineIndex();
            if (lineIndex == textArea.getVisibleLineCount() - 1) {
                return textArea.getPTextBuffer().length();
            } else {
                int y = textArea.getViewCoordinates(new PCoordinates(lineIndex + 1, 0)).y;
                return textArea.getTextIndex(textArea.getNearestCoordinates(new Point(xPixelLocation, y)));
            }
        }
    }
}
