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
import org.json.simple.JSONValue;

import java.io.*;
import java.util.*;

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
            try {
            // load the document (using the specified encoding if one was given)
            File docFile = new File(args[i]);
            System.out.print("Processing document " + docFile + "...");

            String jsonInput = readFile(docFile);
            Object o =  JSONValue.parse(jsonInput);
            JSONObject obj = (JSONObject) o;
            //JSONObject obj = (JSONObject) JSONValue.parse(jsonInput);
            JSONObject dataObject = (JSONObject) obj.get("data");
            String textValue = dataObject.get("text").toString();
            Document doc = Factory.newDocument(textValue);
            // put the document in the corpus
            String content = doc.getContent().toString();
            corpus.add(doc);
              // run the application
            application.execute();
            // remove the document from the corpus again
            corpus.clear();

            // if we want to just write out specific annotation types, we must
            // extract the annotations into a Set
            // output the XML to <inputFile>.out.xml
            String outputFileName = docFile.getName() + ".meta";
            File outputFile = new File(docFile.getParentFile() /* + File.separator + "out" */, outputFileName); // в ту же папку

            // Write output files using the same encoding as the original
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            OutputStreamWriter out;
            if (encoding == null) {
                out = new OutputStreamWriter(bos);
            } else {
                out = new OutputStreamWriter(bos, encoding);
            }
            AnnotationSet set = doc.getAnnotations();
            Iterator it = set.iterator();
            JSONObject resultJson = new JSONObject();
            JSONArray ar = new JSONArray();
            JSONArray aPlace = new JSONArray();
            Integer rank = 0;
            Boolean rus = false;
            Boolean no_rus = false;
                int k = 0;
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
                    address.put("start_node", StartNode);
                    address.put("end_node", EndNode);
                    while (fit.hasNext()) {
                        Map.Entry thisEntry = (Map.Entry) fit.next();
                        String getKey = thisEntry.getKey().toString();
                        String getValue = thisEntry.getValue().toString();
                        address.put(getKey, getValue);
                    }
                    aPlace.add(address);
                }

// цикл добавления местоположения
                if (Type.equals("Loc")) {
                    address.put("start_node", StartNode);
                    address.put("end_node", EndNode);
                    while (fit.hasNext()) {
                        Map.Entry thisEntry = (Map.Entry) fit.next();
                        String getKey = thisEntry.getKey().toString();
                        if (getKey.equals("ProperName"))
                        {
                            String getValue = thisEntry.getValue().toString();
                            address.put("view_name", getValue);
                        }
                        if (getKey.equals("Parent")|getKey.equals("PreParent")|getKey.equals("PrePreParent")|getKey.equals("PrePrePreParent") )
                        {
                            String getValue = thisEntry.getValue().toString();
//                            System.out.println("Parent getValue " + getValue);
                            if (getValue.equals("Россия"))
                            {
                                rus = true;
//                                System.out.println("Россия " + rus);
                            }
                        }
                    }
                    if (!rus.equals(true))no_rus = true;
                    aPlace.add(address);
//                    System.out.println("Loc rus = " + rus);
//                    System.out.println("no_rus = " + no_rus);
                }

// цикл для угроз
                if (Type.equals("Threat_RoadAccident") | Type.equals("Threat_Wildfire")
                        | Type.equals("Threat_BuildingCollapse")  ) {
                    while (fit.hasNext()) {
                        Map.Entry thisEntry = (Map.Entry) fit.next();
                        String getKey = thisEntry.getKey().toString();
                        String getValue = thisEntry.getValue().toString();
                        indicator.put("start_node", StartNode);
                        indicator.put("end_node", EndNode);
                        indicator.put(getKey, getValue);

                        int rank_value = 0;
                        if (getKey.equals("rank")) {
                            rank_value = Integer.valueOf(getValue);
                        }
                        //System.out.println("rank = " + rank);
                        rank = rank + rank_value;
                    }
                    ar.add(indicator);
                    resultJson.put("accident_type", Type);
                    resultJson.put("message_type", "threat");
                }

// цикл для фактов
                if (Type.equals("Fact_RoadAccident") | Type.equals("Fact_Wildfire")
                        | Type.equals("Fact_BuildingCollapse") ) {
                    while (fit.hasNext()) {
                        Map.Entry thisEntry = (Map.Entry) fit.next();
                        String getKey = thisEntry.getKey().toString();
                        String getValue = thisEntry.getValue().toString();
                        indicator.put("start_node", StartNode);
                        indicator.put("end_node", EndNode);
                        indicator.put(getKey, getValue);

                        int rank_value = 0;
                        if (getKey.equals("rank")) {
                            rank_value = Integer.valueOf(getValue);
                        }
                        //System.out.println("rank = " + rank);
                        rank = rank + rank_value;
                    }
                    ar.add(indicator);
                    resultJson.put("accident_type", Type);
                    resultJson.put("message_type", "fact");
                }
                k++;
//                System.out.println("==== " + k);
            }
            resultJson.put("rank", rank);
            if (ar.size() != 0) {
                resultJson.put("indicators", ar);
            }
            resultJson.put("placement", aPlace);
//            System.out.println("rus = " + rus);
//            System.out.println("no_rus = " + no_rus);
            if (rus.equals(true) &&  no_rus.equals(false)) resultJson.put("russia", true);
            if (rus.equals(false) &&  no_rus.equals(true)) resultJson.put("russia", false);

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
        }
        catch (Exception e) {
            System.out.println("Exception " + e);
            corpus.clear();
        }

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
                        "              T`his is the equivalent of \"save preserving format\" in the\n" +
                        "              GATE GUI.  If no -a option is given the whole of each\n" +
                        "              processed document will be output as GateXML (the equivalent\n" +
                        "              of \"save as XML\")."
        );

        System.exit(1);
    }

    private static String readFile(File file) throws IOException {

        StringBuilder fileContents = new StringBuilder((int)file.length());
        Scanner scanner;
        if (encoding == null)
            scanner = new Scanner(file);
        else
            scanner = new Scanner(file, encoding);
        String lineSeparator = System.getProperty("line.separator");

        try {
            while(scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine() + lineSeparator);
            }
            return fileContents.toString();
        } finally {
            scanner.close();
        }
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