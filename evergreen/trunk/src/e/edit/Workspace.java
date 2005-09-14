package e.edit;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
import org.jdesktop.swingworker.SwingWorker;

public class Workspace extends JPanel {
    private EColumn leftColumn = new EColumn();
    private EErrorsWindow errors = new EErrorsWindow("+Errors");

    private String title;
    private String rootDirectory;
    private String buildTarget;
    
    private FindFilesDialog findFilesDialog;
    private OpenQuicklyDialog openQuicklyDialog;
    private EFileDialog openDialog;
    private EFileDialog saveAsDialog;
    
    private ArrayList<String> fileList;
    
    private ETextWindow rememberedTextWindow;
    
    public Workspace(String title, final String rootDirectory) {
        super(new BorderLayout());
        this.title = title;
        this.rootDirectory = FileUtilities.getUserFriendlyName(rootDirectory);
        this.buildTarget = "";
        add(makeUI(), BorderLayout.CENTER);
    }
    
    /**
     * Fills the file list. It can take some time to scan for files, so we do
     * the job in the background.
     */
    public void updateFileList(ChangeListener listener) {
        FileListUpdater fileListUpdater = new FileListUpdater(listener);
        fileListUpdater.execute();
    }
    
    public class FileListUpdater extends SwingWorker<ArrayList<String>, Object> {
        private ChangeListener listener;
        
        public FileListUpdater(ChangeListener listener) {
            this.listener = listener;
            fileList = null;
        }
        
        @Override
        protected ArrayList<String> doInBackground() {
            ArrayList<String> newFileList = scanWorkspaceForFiles();
            // Many file systems will have returned the files not in
            // alphabetical order, so we sort them ourselves here so
            // that users of the list can assume it's in order.
            Collections.sort(newFileList, String.CASE_INSENSITIVE_ORDER);
            fileList = newFileList;
            return fileList;
        }
        
        /**
         * Builds a list of files for Open Quickly. Doesn't bother indexing a workspace if there's no
         * suggestion it's a project (as opposed to a home directory, say): no Makefile or build.xml,
         * and no CVS or SCCS directories are good clues that we're not looking at software.
         */
        private ArrayList<String> scanWorkspaceForFiles() {
            long start = System.currentTimeMillis();
            
            String[] ignoredExtensions = FileUtilities.getArrayOfPathElements(Parameters.getParameter("files.uninterestingExtensions", ""));
            
            ArrayList<String> result = new ArrayList<String>();
            
            // If a workspace is under the user's home directory, we're happy to scan it.
            File userHome = FileUtilities.fileFromString(System.getProperty("user.dir"));
            File workspaceRoot = FileUtilities.fileFromString(getRootDirectory());
            boolean isUnderUserHome;
            try {
                isUnderUserHome = workspaceRoot.equals(userHome) == false && workspaceRoot.getCanonicalPath().startsWith(userHome.getCanonicalPath());
            } catch (IOException ex) {
                return result;
            }
            
            // If we haven't already decided to scan a workspace, see if it contains interesting files that
            // suggest it should be scanned.
            File[] rootFiles = workspaceRoot.listFiles();
            if (isUnderUserHome || (rootFiles != null && isSensibleToScan(rootFiles))) {
                Log.warn("Scanning " + getRootDirectory() + " for interesting files.");
                scanDirectory(getRootDirectory(), ignoredExtensions, result);
                Log.warn("Scan of " + getRootDirectory() + " took " + (System.currentTimeMillis() - start) + "ms; found " + result.size() + " files.");
                Edit.getInstance().showStatus("Scan of '" + getRootDirectory() + "' complete (" + result.size() + " files)");
            } else {
                Log.warn("Skipping scanning of " + getRootDirectory() + " because it doesn't look like a project.");
            }
            
            return result;
        }
        
        /**
         * Tests whether the given directory contents suggest that we should be indexing this workspace.
         * The idea is that if it looks like a software project, it's worth the cost (whatever), but if it doesn't,
         * it may well be ridiculously expensive to scan. Particularly because we blindly follow symlinks.
         */
        private boolean isSensibleToScan(File[] contents) {
            for (File file : contents) {
                if (file.toString().matches(".*(CVS|SCCS|\\.svn|Makefile|makefile|build\\.xml)")) {
                    return true;
                }
            }
            return false;
        }
        
        private void scanDirectory(String directory, String[] ignoredExtensions, ArrayList<String> result) {
            File dir = FileUtilities.fileFromString(directory);
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (FileUtilities.isIgnored(file)) {
                    continue;
                }
                String filename = file.toString();
                if (file.isDirectory()) {
                    scanDirectory(filename, ignoredExtensions, result);
                } else {
                    if (FileUtilities.nameEndsWithOneOf(filename, ignoredExtensions) == false && FileUtilities.isSymbolicLink(file) == false) {
                        int prefixCharsToSkip = FileUtilities.parseUserFriendlyName(getRootDirectory()).length();
                        result.add(filename.substring(prefixCharsToSkip));
                    }
                }
            }
        }
        
        @Override
        public void done() {
            if (listener != null) {
                listener.stateChanged(null);
            }
        }
    }
    
    public String getTitle() {
        return title;
    }
    
    /**
     * Returns the normal, friendly form rather than the OS-canonical one.
     * See also getCanonicalRootDirectory.
     */
    public String getRootDirectory() {
        return rootDirectory;
    }
    
    /**
     * Returns the OS-canonical form rather than the normal, friendly one.
     * See also getRootDirectory.
     */
    public String getCanonicalRootDirectory() throws IOException {
        return FileUtilities.fileFromString(getRootDirectory()).getCanonicalPath() + File.separator;
    }
    
    /**
     * Checks whether the file list is available, and if so, if it's non-empty.
     * It's usually unavailable if the workspace hasn't been scanned yet.
     * The user will be shown an appropriate warning in case of unsuitability.
     */
    public boolean isFileListUnsuitableFor(String purpose) {
        if (fileList == null) {
            Edit.getInstance().showAlert(purpose, "The list of files for " + getTitle() + " is not yet available.");
            return true;
        }
        if (fileList.isEmpty()) {
            Edit.getInstance().showAlert(purpose, "The list of files for " + getTitle() + " is empty.");
            return true;
        }
        return false;
    }
    
    /**
     * Returns a list of the files matching the given regular expression.
     */
    public List<String> getListOfFilesMatching(String regularExpression) {
        // If the user typed a capital, assume that means something, and we
        // should do a case-sensitive search. If everything's lower-case,
        // assume they don't care.
        boolean caseInsensitive = true;
        for (int i = 0; i < regularExpression.length(); ++i) {
            if (Character.isUpperCase(regularExpression.charAt(i))) {
                caseInsensitive = false;
            }
        }
        Pattern pattern = Pattern.compile(regularExpression, caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
        
        // Collect the matches.
        ArrayList<String> result = new ArrayList<String>();
        List<String> allFiles = fileList;
        for (String candidate : allFiles) {
            Matcher matcher = pattern.matcher(candidate);
            if (matcher.find()) {
                result.add(candidate);
            }
        }
        return result;
    }
    
    /**
     * Returns the number of indexed files for this workspace.
     */
    public int getIndexedFileCount() {
        return fileList.size();
    }
    
    public JComponent makeUI() {
        leftColumn.setErrorsWindow(errors);
        registerTextComponent(errors.getText());
        return leftColumn;
    }
    
    /** Tests whether this workspace is empty. A workspace is still considered empty if all it contains is an errors window. */
    public boolean isEmpty() {
        return leftColumn.getTextWindows().length == 0;
    }
    
    /** Returns an array of this workspace's dirty text windows. */
    public ETextWindow[] getDirtyTextWindows() {
        ArrayList<ETextWindow> dirtyTextWindows = new ArrayList<ETextWindow>();
        ETextWindow[] textWindows = leftColumn.getTextWindows();
        for (ETextWindow textWindow : textWindows) {
            if (textWindow.isDirty()) {
                dirtyTextWindows.add(textWindow);
            }
        }
        return dirtyTextWindows.toArray(new ETextWindow[dirtyTextWindows.size()]);
    }
    
    public EWindow findWindowByName(String name) {
        //FIXME: if the file's not already open, we need to find it and open it.
        return leftColumn.findWindowByName(name);
    }
    
    public static boolean isAbsolute(String filename) {
        if (GuiUtilities.isWindows()) {
            /* FIXME: is this a good test for Windows? What about \\ names? */
            return (filename.length() > 1) && (filename.charAt(1) == ':');
        } else {
            return (filename.length() > 0) && (filename.charAt(0) == '/');
        }
    }
    
    /** Returns the EWindow corresponding to the given file. If the file's open, shows the given address. */
    public EWindow findIfAlreadyOpen(String filename, String address) {
        /* Check we don't already have this open as a file or directory. */
        EWindow window = findWindowByName(filename);
        if (window != null) {
            leftColumn.setSelectedWindow(window);
            window.ensureSufficientlyVisible();
            ETextWindow textWindow = (ETextWindow) window;
            textWindow.jumpToAddress(address);
            window.requestFocus();
            return window;
        }
        return null;
    }
    
    public void registerTextComponent(PTextArea textComponent) {
        Edit.getInstance().getAdvisor().registerTextComponent(textComponent);
    }
    
    public void unregisterTextComponent(PTextArea textComponent) {
        Edit.getInstance().getAdvisor().unregisterTextComponent(textComponent);
    }
    
    public EWindow addViewerForFile(String filename, String address) {
        Edit.getInstance().showStatus("Opening " + filename + "...");
        EWindow window = null;
        try {
            ETextWindow newWindow = new ETextWindow(filename);
            registerTextComponent(newWindow.getText());
            window = addViewer(newWindow, address);
        } catch (Exception ex) {
            Log.warn("Exception while opening file", ex);
        }
        Edit.getInstance().showStatus("Opened " + filename);
        return window;
    }
    
    public EWindow addViewer(EWindow viewer) {
        addViewer(viewer, null);
        return viewer;
    }
    
    public EWindow addViewer(EWindow viewer, final String address) {
        leftColumn.addComponent(viewer);
        if (address != null) {
            final ETextWindow textWindow = (ETextWindow) viewer;
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    textWindow.jumpToAddress(address);
                }
            });
        }
        viewer.requestFocus();
        return viewer;
    }
    
    public synchronized void reportError(final String error) {
        errors.append(error);
    }
    
    public EErrorsWindow getErrorsWindow() {
        return errors;
    }
    
    /**
     * Checks whether this workspace has unsaved files before performing some action.
     * Offers the user the choice of continuing without saving, continuing after saving, or
     * canceling.
     * Returns true if you should continue with your action, false if the user hit 'Cancel'.
     */
    public boolean prepareForAction(String activity, String unsavedDataPrompt) {
        ETextWindow[] dirtyWindows = getDirtyTextWindows();
        if (dirtyWindows.length > 0) {
            String choice = Edit.getInstance().askQuestion(activity, unsavedDataPrompt, "Save All", "Don't Save");
            if (choice.equals("Cancel")) {
                return false;
            }
            boolean saveAll = choice.equals("Save All");
            if (saveAll == false) {
                return true;
            }
            boolean okay = saveAll();
            if (okay == false) {
                // Pretend the user canceled, because we shouldn't pretend that everything went okay.
                return false;
            }
        }
        return true;
    }
    
    /**
     * Attempts to save all the dirty files on this workspace.
     * Returns true if all saves were successful, false otherwise.
     */
    public boolean saveAll() {
        for (ETextWindow dirtyWindow : getDirtyTextWindows()) {
            boolean savedOkay = dirtyWindow.save();
            if (savedOkay == false) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Shows the "Find File" dialog with the given String as the contents of
     * the text field. Use the empty string to retain the current contents.
     * There's no way to empty the text field's contents, but that sounds like
     * a bad idea anyway. Either you've got a better suggestion than what the
     * user last typed, or you should leave things as they are.
     */
    public void showFindFilesDialog(String pattern, String filenamePattern) {
        if (findFilesDialog == null) {
            findFilesDialog = new FindFilesDialog(this);
        }
        if (pattern != null && pattern.length() > 0) {
            findFilesDialog.setPattern(pattern);
        }
        if (filenamePattern != null && filenamePattern.length() > 0) {
            findFilesDialog.setFilenamePattern(filenamePattern);
        }
        findFilesDialog.showDialog();
    }
    
    /**
     * Shows the "Open Quickly" dialog with the given String as the contents
     * of the text field. Use the empty string to retain the current contents.
     * There's no way to empty the text field's contents, but that sounds like
     * a bad idea anyway. Either you've got a better suggestion than what the
     * user last typed, or you should leave things as they are.
     */
    public void showOpenQuicklyDialog(String filenamePattern) {
        if (openQuicklyDialog == null) {
            openQuicklyDialog = new OpenQuicklyDialog(this);
        }
        if (filenamePattern != null && filenamePattern.length() > 0 && filenamePattern.contains("\n") == false) {
            openQuicklyDialog.setFilenamePattern(filenamePattern);
        }
        openQuicklyDialog.showDialog();
    }
    
    public void showOpenDialog() {
        if (openDialog == null) {
            openDialog = EFileDialog.makeOpenDialog(Edit.getInstance().getFrame(), getRootDirectory());
        }
        openDialog.show();
        String name = openDialog.getFile();
        if (name != null) {
            Edit.getInstance().openFile(name);
        }
    }

    /** Returns the chosen save-as name, or null. */
    public String showSaveAsDialog() {
        if (saveAsDialog == null) {
            saveAsDialog = EFileDialog.makeSaveDialog(Edit.getInstance().getFrame(), getRootDirectory());
        }
        saveAsDialog.show();
        return saveAsDialog.getFile();
    }
    
    public void takeWindow(EWindow window) {
        window.removeFromColumn();
        leftColumn.addComponent(window);
    }
    
    public void moveFilesToBestWorkspaces() {
        for (ETextWindow textWindow : leftColumn.getTextWindows()) {
            Workspace bestWorkspace = Edit.getInstance().getBestWorkspaceForFilename(textWindow.getFilename());
            if (bestWorkspace != this) {
                bestWorkspace.takeWindow(textWindow);
            }
        }        
    }
    
    public void rememberFocusedTextWindow(ETextWindow textWindow) {
        rememberedTextWindow = textWindow;
    }
    
    public void restoreFocusToRememberedTextWindow() {
        if (rememberedTextWindow != null) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    rememberedTextWindow.requestFocus();
                }
            });
        }
    }
    
    public String getBuildTarget() {
        return buildTarget;
    }
    
    public void setBuildTarget(String buildTarget) {
        this.buildTarget = buildTarget;
    }
    
    public void serializeAsXml(org.w3c.dom.Document document, org.w3c.dom.Element workspaces) {
        org.w3c.dom.Element workspace = document.createElement("workspace");
        workspaces.appendChild(workspace);
        workspace.setAttribute("name", getTitle());
        workspace.setAttribute("root", getRootDirectory());
        workspace.setAttribute("buildTarget", getBuildTarget());
        for (ETextWindow textWindow : leftColumn.getTextWindows()) {
            org.w3c.dom.Element file = document.createElement("file");
            workspace.appendChild(file);
            file.setAttribute("name", textWindow.getFilename() + textWindow.getAddress());
        }
    }
}
