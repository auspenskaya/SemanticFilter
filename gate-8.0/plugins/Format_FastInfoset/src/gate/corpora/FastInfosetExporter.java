/*
 * FastInfosetExporter.java
 * 
 * Copyright (c) 2012-2013, The University of Sheffield. See the file
 * COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * Mark A. Greenwood, 01/08/2013
 */

package gate.corpora;

import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.gui.MainFrame;
import gate.gui.NameBearerHandle;
import gate.gui.ResourceHelper;
import gate.swing.XJFileChooser;
import gate.util.Err;
import gate.util.ExtensionFileFilter;
import gate.util.Files;
import gate.util.InvalidOffsetException;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.sun.xml.fastinfoset.stax.StAXDocumentSerializer;

@SuppressWarnings("serial")
@CreoleResource(name = "Fast Infoset Exporter", tool = true, autoinstances = @AutoInstance, comment = "Export GATE documents to GATE XML stored in the binary FastInfoset format", helpURL = "http://gate.ac.uk/userguide/sec:creole:fastinfoset")
public class FastInfosetExporter extends ResourceHelper {

  @Override
  protected List<Action> buildActions(final NameBearerHandle handle) {
    List<Action> actions = new ArrayList<Action>();

    if(handle.getTarget() instanceof Document) {
      actions.add(new AbstractAction("Save as Fast Infoset XML...") {

        @Override
        public void actionPerformed(ActionEvent e) {

          Runnable runableAction = new Runnable() {

            @Override
            public void run() {
              XJFileChooser fileChooser = MainFrame.getFileChooser();
              ExtensionFileFilter filter =
                  new ExtensionFileFilter("Fast Infoset XML Files (*.finf)",
                      "finf");
              fileChooser.addChoosableFileFilter(filter);
              fileChooser.setMultiSelectionEnabled(false);
              fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
              fileChooser.setDialogTitle("Saving as Fast Infoset XML");

              Document doc = (Document)handle.getTarget();
              if(doc.getSourceUrl() != null) {
                String fileName = "";
                try {
                  fileName = doc.getSourceUrl().toURI().getPath().trim();
                } catch(URISyntaxException e) {
                  fileName = doc.getSourceUrl().getPath().trim();
                }
                if(fileName.equals("") || fileName.equals("/")) {
                  if(doc.getNamedAnnotationSets().containsKey(
                      "Original markups")
                      && !doc.getAnnotations("Original markups").get("title")
                          .isEmpty()) {
                    // use the title annotation if any
                    try {
                      fileName =
                          doc.getContent()
                              .getContent(
                                  doc.getAnnotations("Original markups")
                                      .get("title").firstNode().getOffset(),
                                  doc.getAnnotations("Original markups")
                                      .get("title").lastNode().getOffset())
                              .toString();
                    } catch(InvalidOffsetException e) {
                      e.printStackTrace();
                    }
                  } else {
                    fileName = doc.getSourceUrl().toString();
                  }
                  // cleans the file name
                  fileName = fileName.replaceAll("/", "_");
                } else {
                  // replaces the extension with .finf
                  fileName = fileName.replaceAll("\\.[a-zA-Z]{1,4}$", ".finf");
                }
                // cleans the file name
                fileName = fileName.replaceAll("[^/a-zA-Z0-9._-]", "_");
                fileName = fileName.replaceAll("__+", "_");
                // adds a .finf extension if not present
                if(!fileName.endsWith(".finf")) {
                  fileName += ".finf";
                }
                File file = new File(fileName);
                fileChooser.ensureFileIsVisible(file);
                fileChooser.setSelectedFile(file);
              }

              if(fileChooser.showSaveDialog(MainFrame.getInstance()) != JFileChooser.APPROVE_OPTION)
                return;

              File selectedFile = fileChooser.getSelectedFile();
              if(selectedFile == null) return;
              long start = System.currentTimeMillis();
              handle.statusChanged("Saving as Fast Infoset XML to "
                  + selectedFile.toString() + "...");
              try {
                MainFrame.lockGUI("Exporting...");

                export(doc, selectedFile);
              } catch(Exception ex) {
                MainFrame.unlockGUI();
                JOptionPane.showMessageDialog(MainFrame.getInstance(),
                    "Could not create write file:" + ex.toString(), "GATE",
                    JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace(Err.getPrintWriter());
                return;
              } finally {
                MainFrame.unlockGUI();
              }
              long time = System.currentTimeMillis() - start;
              handle.statusChanged("Finished saving as Fast Infoset XML into "
                  + " the file: " + selectedFile.toString() + " in "
                  + ((double)time) / 1000 + " s");
            }
          };
          Thread thread = new Thread(runableAction, "Fast Infoset Exporter");
          thread.setPriority(Thread.MIN_PRIORITY);
          thread.start();
        }

      });
    } else if(handle.getTarget() instanceof Corpus) {
      actions.add(new AbstractAction("Save as Fast Infoset XML...") {

        @Override
        public void actionPerformed(ActionEvent e) {
          Runnable runnable = new Runnable() {

            @Override
            public void run() {
              try {
                // we need a directory
                XJFileChooser fileChooser = MainFrame.getFileChooser();
                fileChooser
                    .setDialogTitle("Select the directory that will contain the corpus");
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

                if(fileChooser.showDialog(MainFrame.getInstance(), "Select") != JFileChooser.APPROVE_OPTION)
                  return;

                File dir = fileChooser.getSelectedFile();
                // create the top directory if needed
                if(!dir.exists()) {
                  if(!dir.mkdirs()) {
                    JOptionPane.showMessageDialog(MainFrame.getInstance(),
                        "Could not create top directory!", "GATE",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                  }
                }

                MainFrame.lockGUI("Saving...");

                // iterate through all the docs and save each of them as
                // finf
                Corpus corpus = (Corpus)handle.getTarget();
                Iterator<Document> docIter = corpus.iterator();
                boolean overwriteAll = false;
                int docCnt = corpus.size();
                int currentDocIndex = 0;
                Set<String> usedFileNames = new HashSet<String>();
                while(docIter.hasNext()) {
                  boolean docWasLoaded =
                      corpus.isDocumentLoaded(currentDocIndex);
                  Document currentDoc = docIter.next();
                  URL sourceURL = currentDoc.getSourceUrl();
                  String fileName = null;
                  if(sourceURL != null) {
                    fileName = sourceURL.getPath();
                    fileName = Files.getLastPathComponent(fileName);
                  }
                  if(fileName == null || fileName.length() == 0) {
                    fileName = currentDoc.getName();
                  }
                  // makes sure that the filename does not contain any
                  // forbidden character
                  fileName = fileName.replaceAll("[\\/:\\*\\?\"<>|]", "_");
                  if(fileName.toLowerCase().endsWith(".finf")) {
                    fileName = fileName.substring(0, fileName.length() - 5);
                  }
                  if(usedFileNames.contains(fileName)) {
                    // name clash -> add unique ID
                    String fileNameBase = fileName;
                    int uniqId = 0;
                    fileName = fileNameBase + "-" + uniqId++;
                    while(usedFileNames.contains(fileName)) {
                      fileName = fileNameBase + "-" + uniqId++;
                    }
                  }
                  usedFileNames.add(fileName);
                  if(!fileName.toLowerCase().endsWith(".finf"))
                    fileName += ".finf";
                  File docFile = null;
                  boolean nameOK = false;
                  do {
                    docFile = new File(dir, fileName);
                    if(docFile.exists() && !overwriteAll) {
                      // ask the user if we can overwrite the file
                      Object[] options =
                          new Object[]{"Yes", "All", "No", "Cancel"};
                      MainFrame.unlockGUI();
                      int answer =
                          JOptionPane.showOptionDialog(MainFrame.getInstance(),
                              "File " + docFile.getName()
                                  + " already exists!\n" + "Overwrite?",
                              "GATE", JOptionPane.DEFAULT_OPTION,
                              JOptionPane.WARNING_MESSAGE, null, options,
                              options[2]);
                      MainFrame.lockGUI("Saving...");
                      switch(answer){
                        case 0: {
                          nameOK = true;
                          break;
                        }
                        case 1: {
                          nameOK = true;
                          overwriteAll = true;
                          break;
                        }
                        case 2: {
                          // user said NO, allow them to provide an
                          // alternative name;
                          MainFrame.unlockGUI();
                          fileName =
                              (String)JOptionPane.showInputDialog(
                                  MainFrame.getInstance(),
                                  "Please provide an alternative file name",
                                  "GATE", JOptionPane.QUESTION_MESSAGE, null,
                                  null, fileName);
                          if(fileName == null) {
                            handle.processFinished();
                            return;
                          }
                          MainFrame.lockGUI("Saving");
                          break;
                        }
                        case 3: {
                          // user gave up; return
                          handle.processFinished();
                          return;
                        }
                      }

                    } else {
                      nameOK = true;
                    }
                  } while(!nameOK);
                  // save the file
                  try {
                    // do the actual exporting
                    export(currentDoc, docFile);
                  } catch(Exception ioe) {
                    MainFrame.unlockGUI();
                    JOptionPane.showMessageDialog(MainFrame.getInstance(),
                        "Could not create write file:" + ioe.toString(),
                        "GATE", JOptionPane.ERROR_MESSAGE);
                    ioe.printStackTrace(Err.getPrintWriter());
                    return;
                  }

                  handle.statusChanged(currentDoc.getName() + " saved");
                  // close the doc if it wasn't already loaded
                  if(!docWasLoaded) {
                    corpus.unloadDocument(currentDoc);
                    Factory.deleteResource(currentDoc);
                  }

                  handle.progressChanged(100 * currentDocIndex++ / docCnt);
                }// while(docIter.hasNext())
                handle.statusChanged("Corpus Saved");
                handle.processFinished();

              } finally {
                MainFrame.unlockGUI();
              }
            }
          };
          Thread thread =
              new Thread(Thread.currentThread().getThreadGroup(), runnable,
                  "Corpus Fast Infoset XML dumper");
          thread.setPriority(Thread.MIN_PRIORITY);
          thread.start();

        }

      });
    }

    return actions;
  }

  /**
   * A static utility method that exports the specified GATE document to a Fast
   * Infoset file.
   * 
   * @param doc
   *          the {@link gate.Document} instance to export
   * @param file
   *          the {@link java.io.File}
   * @throws Exception
   */
  public static void export(Document doc, File file) throws Exception {
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      export(doc, out);
    } finally {
      out.close();
    }
  }

  public static void export(Document doc, OutputStream out) throws Exception {

    StAXDocumentSerializer xsw = new StAXDocumentSerializer(out);

    xsw.writeStartDocument("1.0");
    DocumentStaxUtils.writeDocument(doc, xsw, "");
    xsw.writeEndDocument();
    xsw.flush();
    xsw.close();
  }

  public static void main(String args[]) throws Exception {
    // initialise GATE and load the plugin (which creates an autoinstance of
    // the Resource Helper)
    Gate.init();
    Gate.getCreoleRegister().registerDirectories(
        (new File(Gate.getGateHome(), "plugins/Format_FastInfoset")).toURI()
            .toURL());

    // get the auto created instance of the Resource Helper
    ResourceHelper rh =
        (ResourceHelper)Gate.getCreoleRegister()
            .getAllInstances("gate.corpora.FastInfosetExporter").iterator()
            .next();

    // create a simple test document
    Document doc =
        Factory.newDocument("A test of the Resource Handler API access");

    // use the Resource Helper to export the document
    rh.call("export", doc, new File("resource-handler-test.finf"));
  }
}
