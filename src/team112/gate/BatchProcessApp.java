/*

 */
package team112.gate;

import gate.*;
import gate.annotation.AnnotationImpl;
import gate.util.*;
import gate.util.asm.Type;
import gate.util.persistence.PersistenceManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStreamWriter;

/**
 * This class ilustrates how to do simple batch processing with GATE.  It loads
 * an application from a .gapp file (created using "Save application state" in
 * the GATE GUI), and runs the contained application over one or more files.
 * The results are written out to XML files, either in GateXML format (all
 * annotation sets preserved, as in "save as XML" in the GUI), or with inline
 * XML tags taken from the default annotation set (as in "save preserving
 * format").  In this example, the output file names are simply the input file
 * names with ".out.xml" appended.
 *
 * To keep the example simple, we do not do any exception handling - any error
 * will cause the process to abort.
 */
public class BatchProcessApp {

    /**
     * The main entry point.  First we parse the command line options (see
     * usage() method for details), then we take all remaining command line
     * parameters to be file names to process.  Each file is loaded, processed
     * using the application and the results written to the output file
     * (inputFile.out.xml).
     */
    public static void main(String[] args) throws Exception {
        parseCommandLine(args);

        // initialise GATE - this must be done before calling any GATE APIs
        Gate.init();

        // load the saved application
        CorpusController application =
                (CorpusController)PersistenceManager.loadObjectFromFile(gappFile);

        // Create a Corpus to use.  We recycle the same Corpus object for each
        // iteration.  The string parameter to newCorpus() is simply the
        // GATE-internal name to use for the corpus.  It has no particular
        // significance.
        Corpus corpus = Factory.newCorpus("BatchProcessApp Corpus");
        application.setCorpus(corpus);

        // process the files one by one
        for(int i = firstFile; i < args.length; i++) {
            // load the document (using the specified encoding if one was given)
            File docFile = new File(args[i]);
            System.out.print("Processing document " + docFile + "...");
            Document doc = Factory.newDocument(docFile.toURL(), encoding);

            // put the document in the corpus
            corpus.add(doc);

            // run the application
            application.execute();

            // remove the document from the corpus again
            corpus.clear();

            // if we want to just write out specific annotation types, we must
            // extract the annotations into a Set
            // output the XML to <inputFile>.out.xml
            String outputFileName = docFile.getName() + ".out.json";
            File outputFile = new File(docFile.getParentFile(), outputFileName);

            // Write output files using the same encoding as the original
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            OutputStreamWriter out;
            if(encoding == null) {
                out = new OutputStreamWriter(bos);
            }
            else {
                out = new OutputStreamWriter(bos, encoding);
            }
            AnnotationSet set = doc.getAnnotations();
            Iterator it = set.iterator();
            JSONObject resultJson = new JSONObject();
            JSONArray ar = new JSONArray();
            Integer rank = 0;
            while (it.hasNext()) {
                AnnotationImpl ann = (AnnotationImpl) it.next();
                String Type = ann.getType().toString();
                gate.FeatureMap map = ann.getFeatures();
                Iterator fit = map.entrySet().iterator();
                Integer StartNode = ann.getStartNode().getOffset().intValue();
                Integer EndNode = ann.getEndNode().getOffset().intValue();
                JSONObject address = new JSONObject();
                JSONObject indicator = new JSONObject();

// цикл добавления адреса
                if (Type.equals("Placement")) {
                    address.put("StartNode", StartNode);
                    address.put("EndNode", EndNode);
                    while (fit.hasNext()) {
                        Map.Entry thisEntry = (Map.Entry) fit.next();
                        String getKey = thisEntry.getKey().toString();
                        String getValue = thisEntry.getValue().toString();
                        address.put(getKey, getValue);
                    }

                        resultJson.put("Placement", address);
                    }
// цикл добавления признаков угроз
                if (Type.equals("Threat_RoadAccident")) {
                    while (fit.hasNext()) {
                        Map.Entry thisEntry = (Map.Entry) fit.next();
                        String getKey = thisEntry.getKey().toString();
                        String getValue = thisEntry.getValue().toString();
                        indicator.put("AccidentType", Type);
                        indicator.put("StartNode", StartNode);
                        indicator.put("EndNode", EndNode);
                        indicator.put(getKey, getValue);
                        ar.add(indicator);
                        int rank_value = 0;
                        if (getKey.equals("rank")) {
                            rank_value = Integer.valueOf(getValue);
                        }
                        //System.out.println("rank = " + rank);
                        rank = rank + rank_value;
                    }
                    resultJson.put("message_type", "threat");
                }
            }
            if (ar.size() != 0)
            resultJson.put("rank", rank);
            resultJson.put("indicators", ar);
            if (resultJson.size() != 0)
                out.write(resultJson.toString());




//            if(annotTypesToWrite != null) {
//                // Create a temporary Set to hold the annotations we wish to write out
//                Set annotationsToWrite = new HashSet();
//
//                // we only extract annotations from the default (unnamed) AnnotationSet
//                // in this example
//                AnnotationSet defaultAnnots = doc.getAnnotations();
//                Iterator annotTypesIt = annotTypesToWrite.iterator();
//                while(annotTypesIt.hasNext()) {
//                    // extract all the annotations of each requested type and add them to
//                    // the temporary set
//                    AnnotationSet annotsOfThisType =
//                            defaultAnnots.get((String)annotTypesIt.next());
//                    if(annotsOfThisType != null) {
//                        annotationsToWrite.addAll(annotsOfThisType);
//                    }
//                }
//
//                // create the XML string using these annotations
//                docXMLString = doc.toXml(annotationsToWrite);
//            }
//            // otherwise, just write out the whole document as GateXML
//            else {
//                docXMLString = doc.toXml();
//            }

            // Release the document, as it is no longer needed
            Factory.deleteResource(doc);




            out.close();
            System.out.println("done");
        } // for each file

        System.out.println("All done");
    } // void main(String[] args)


    /**
     * Parse command line options.
     */
    private static void parseCommandLine(String[] args) throws Exception {
        int i;
        // iterate over all options (arguments starting with '-')
        for(i = 0; i < args.length && args[i].charAt(0) == '-'; i++) {
            switch(args[i].charAt(1)) {
                // -a type = write out annotations of type a.
                case 'a':
                    if(annotTypesToWrite == null) annotTypesToWrite = new ArrayList();
                    annotTypesToWrite.add(args[++i]);
                    break;

                // -g gappFile = path to the saved application
                case 'g':
                    gappFile = new File(args[++i]);
                    break;

                // -e encoding = character encoding for documents
                case 'e':
                    encoding = args[++i];
                    break;

                default:
                    System.err.println("Unrecognised option " + args[i]);
                    usage();
            }
        }

        // set index of the first non-option argument, which we take as the first
        // file to process
        firstFile = i;

        // sanity check other arguments
        if(gappFile == null) {
            System.err.println("No .gapp file specified");
            usage();
        }
    }

    /**
     * Print a usage message and exit.
     */
    private static final void usage() {
        System.err.println(
                "Usage:\n" +
                        "   java team112.gate.BatchProcessApp -g <gappFile> [-e encoding]\n" +
                        "            [-a annotType] [-a annotType] file1 file2 ... fileN\n" +
                        "\n" +
                        "-g gappFile : (required) the path to the saved application state we are\n" +
                        "              to run over the given documents.  This application must be\n" +
                        "              a \"corpus pipeline\" or a \"conditional corpus pipeline\".\n" +
                        "\n" +
                        "-e encoding : (optional) the character encoding of the source documents.\n" +
                        "              If not specified, the platform default encoding (currently\n" +
                        "              \"" + System.getProperty("file.encoding") + "\") is assumed.\n" +
                        "\n" +
                        "-a type     : (optional) write out just the annotations of this type as\n" +
                        "              inline XML tags.  Multiple -a options are allowed, and\n" +
                        "              annotations of all the specified types will be output.\n" +
                        "              This is the equivalent of \"save preserving format\" in the\n" +
                        "              GATE GUI.  If no -a option is given the whole of each\n" +
                        "              processed document will be output as GateXML (the equivalent\n" +
                        "              of \"save as XML\")."
        );

        System.exit(1);
    }

    /** Index of the first non-option argument on the command line. */
    private static int firstFile = 0;

    /** Path to the saved application file. */
    private static File gappFile = null;

    /**
     * List of annotation types to write out.  If null, write everything as
     * GateXML.
     */
    private static List annotTypesToWrite = null;

    /**
     * The character encoding to use when loading the docments.  If null, the
     * platform default encoding is used.
     */
    private static String encoding = null;
}