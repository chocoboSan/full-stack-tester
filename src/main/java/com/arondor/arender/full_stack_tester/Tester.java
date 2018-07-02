
package com.arondor.arender.full_stack_tester;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Hello world!
 *
 */
public class Tester
{

    private static final Logger logger = Logger.getLogger(Tester.class.getName());

    private static final String URL_PARAM = "url";

    private static final String FOLDER_PATH_PARAM = "folder";

    private static final String FILE_LIST_PATH_PARAM = "filelist";

    private static final String DUMP_IMAGES_PARAM = "dump";

    private static final String FULL_RUN_PARAM = "full";

    private static ScheduledThreadPoolExecutor imagePoolExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 10);

    private static ScheduledThreadPoolExecutor pageContentsPoolExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 10);

    private static ScheduledThreadPoolExecutor documentPoolExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors());

    private static boolean dumpImages = false;

    private static boolean fullRun = false;

    public static void main(String[] args) throws IOException, InterruptedException
    {
        DefaultParser parser = new DefaultParser();
        Options options = generateOptions();

        CommandLine parse;
        try
        {
            parse = parser.parse(options, args);
        }
        catch (ParseException e1)
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar full-stack-tester-{VERSION}-jar-with-dependencies.jar", options, true);
            return;
        }
        // expected : parameter 1 => base context of ARender
        // parameter 2 => filename containing IDs to be played onto
        // openExternalDocument.jsp of
        // ARender
        String baseURLARender = parse.getOptionValue(URL_PARAM);
        if (!baseURLARender.endsWith("/"))
        {
            baseURLARender = baseURLARender + "/";
        }
        final String urlARenderFinal = baseURLARender;
        File file = null;
        String optionValue = parse.getOptionValue(FOLDER_PATH_PARAM);
        if (optionValue != null)
        {
            file = new File(optionValue);
            if (file.isDirectory())
            {
                // create and open tmp file containing all files of this folder
                // and
                // subfolders
                file = createTmpFile(file);
            }
        }
        String optionValue2 = parse.getOptionValue(FILE_LIST_PATH_PARAM);
        if (optionValue2 != null)
        {
            file = new File(optionValue2);
            if (file.isFile())
            {
                // no-op, list is provided
            }
        }
        if (file == null)
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar full-stack-tester-{VERSION}-jar-with-dependencies.jar", options, true);
            return;
        }
        if (parse.hasOption(DUMP_IMAGES_PARAM))
        {
            dumpImages = true;
        }
        if (parse.hasOption(FULL_RUN_PARAM))
        {
            fullRun = true;
        }
        List<String> openExternalParams = new ArrayList<String>();
        BufferedReader bis = new BufferedReader(new FileReader(file));
        String parameters;
        while ((parameters = bis.readLine()) != null)
        {
            final String finalParams = parameters;
            openExternalParams.add(finalParams);
        }
        Collections.shuffle(openExternalParams);
        for (final String finalParams : openExternalParams)
        {
            documentPoolExecutor.submit(new Runnable()
            {
                public void run()
                {
                    try
                    {

                        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(
                                urlARenderFinal + "openExternalDocument.jsp?" + finalParams).openConnection();
                        if (httpURLConnection.getResponseCode() == 200)
                        {
                            String docId = IOUtils.toString(httpURLConnection.getInputStream()).trim();
                            parseLayoutForId(urlARenderFinal, docId);
                        }
                        else
                        {
                            logger.log(Level.SEVERE, "File cannot be parsed : " + finalParams + " error : "
                                    + IOUtils.toString(httpURLConnection.getErrorStream()));
                        }
                    }
                    catch (Exception e)
                    {
                        logger.log(Level.SEVERE, "File cannot be parsed : " + finalParams, e);
                    }
                }
            });
        }

        documentPoolExecutor.shutdown();
        documentPoolExecutor.awaitTermination(120, TimeUnit.MINUTES);
        imagePoolExecutor.shutdown();
        imagePoolExecutor.awaitTermination(120, TimeUnit.MINUTES);

        if (fullRun)
        {

            while (documentPoolExecutor.getActiveCount() > 0)
            {
                logger.log(Level.SEVERE, "Running full mode, still have tasks to do :\n" + "documents: "
                        + documentPoolExecutor.getActiveCount() + "\nimages : " + imagePoolExecutor.getActiveCount());
                if (documentPoolExecutor.getActiveCount() > 0)
                {
                    documentPoolExecutor.awaitTermination(120, TimeUnit.MINUTES);
                }
                else if (imagePoolExecutor.getActiveCount() > 0)
                {
                    imagePoolExecutor.awaitTermination(120, TimeUnit.MINUTES);
                }
                else
                {
                    break;
                }
            }
        }
    }

    private static Options generateOptions()
    {
        Option baseUrl = new Option(URL_PARAM, true, "base url of deployed ARender");
        baseUrl.setRequired(true);
        Option folderName = new Option(FOLDER_PATH_PARAM, true, "path containing all documents to test in ARender");
        folderName.setRequired(false);
        Option dumpWhiteImages = new Option(DUMP_IMAGES_PARAM, false, "Dump all white images into a specific folder");
        dumpWhiteImages.setRequired(false);
        Option fullRun = new Option(FULL_RUN_PARAM, false, "Asks for a full run instead of 120mn load");
        fullRun.setRequired(false);
        Option filelist = new Option(FILE_LIST_PATH_PARAM, true, "Run with a predefined list of parameters");
        filelist.setRequired(false);

        Options options = new Options();
        options.addOption(baseUrl);
        options.addOption(folderName);
        options.addOption(dumpWhiteImages);
        options.addOption(fullRun);
        options.addOption(filelist);
        return options;
    }

    private static File createTmpFile(File file) throws IOException
    {
        File tmpFile = Files.createTempFile("fakeJmeter", ".txt").toFile();
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
        listAndRecurse(bw, file);
        bw.close();
        return tmpFile;
    }

    private static void listAndRecurse(BufferedWriter bw, File file) throws IOException
    {
        String[] list = file.list();
        if (list == null)
        {
            return;
        }
        for (String subfile : list)
        {
            File currentFile = new File(file.getAbsolutePath() + File.separator + subfile);
            if (currentFile.isDirectory())
            {
                listAndRecurse(bw, currentFile);
            }
            else if (currentFile.exists())
            {
                bw.write("url=" + currentFile.getAbsolutePath() + "\n");
            }
        }
    }

    private static void resolveChildren(JsonObject object, String urlARenderFinal)
            throws MalformedURLException, IOException
    {
        String docId = extractDocumentIdStringValue(object);
        JsonArray children = object.getAsJsonArray("children");
        for (int i = 0; i < children.size(); i++)
        {
            // build documentId
            JsonObject childId = children.get(i).getAsJsonObject();
            String finalChildId = revertIdToRoot(childId.get("documentId").getAsJsonObject());
            parseLayoutForId(urlARenderFinal, finalChildId);
        }
    }

    private static String revertIdToRoot(JsonObject childId)
    {
        if (childId.has("fatherDocumentId"))
        {
            return revertIdToRoot(childId.get("fatherDocumentId").getAsJsonObject()) + "/"
                    + childId.get("stringValue").getAsString();
        }
        else
        {
            return childId.get("stringValue").getAsString();
        }
    }

    private static void handleJsonDimensions(JsonObject object, String baseURLARender)
    {
        String docId = revertIdToRoot(object.get("documentId").getAsJsonObject());
        JsonArray array = object.getAsJsonArray("pageDimensionsList");
        for (int i = 0; i < array.size(); i++)
        {
            fetchPage(baseURLARender, docId, i, (int) ((JsonObject) array.get(i)).get("width").getAsFloat());
        }
    }

    private static String extractDocumentIdStringValue(JsonObject object)
    {
        return ((JsonObject) object.get("documentId")).get("stringValue").getAsString();
    }

    private static void fetchPage(final String baseURLARender, final String docId, final int pageNumber,
            final int width)
    {
        imagePoolExecutor.submit(new Runnable()
        {
            public void run()
            {
                try
                {
                    String URLimageSerlvet = baseURLARender + "arendergwt/imageServlet?uuid=" + docId + "&pagePosition="
                            + pageNumber + "&desc=IM_" + width + "_0";
                    logger.info("Calling : " + URLimageSerlvet);
                    final HttpURLConnection openConnection = (HttpURLConnection) new URL(URLimageSerlvet)
                            .openConnection();
                    if (openConnection.getResponseCode() == 200)
                    {
                        if (dumpImages)
                        {
                            String imageFolder = "./images/";
                            if (!Files.exists(Paths.get(imageFolder)))
                            {
                                Files.createDirectory(Paths.get(imageFolder));
                            }
                            BufferedImage img = ImageIO.read(new URL(URLimageSerlvet));
                            // img = img.getScaledInstance(100, -1,
                            // Image.SCALE_FAST);
                            int w = img.getWidth(null);
                            int h = img.getHeight(null);
                            int[] pixels = new int[w * h];
                            PixelGrabber pg = new PixelGrabber(img, 0, 0, w, h, pixels, 0, w);
                            try
                            {
                                pg.grabPixels();
                                long totalPix = 0;
                                long whitePix = 0;
                                for (int pixel : pixels)
                                {
                                    Color color = new Color(pixel);
                                    if (color.getRGB() == Color.WHITE.getRGB())
                                    {
                                        whitePix++;
                                    }
                                    totalPix++;
                                }
                                if (whitePix == totalPix)
                                {
                                    ImageIO.write(img, "png",
                                            new File(imageFolder + docId + "-" + pageNumber + ".png"));
                                }
                            }
                            catch (InterruptedException e)
                            {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                        else
                        {
                            IOUtils.readFully(openConnection.getInputStream(), (int) Long.MAX_VALUE);
                        }
                    }
                    else
                    {
                        logger.log(Level.SEVERE, "couldn't not access URL, error code : "
                                + IOUtils.toString(openConnection.getErrorStream()));
                    }
                }
                catch (MalformedURLException e)
                {
                    logger.log(Level.SEVERE, "couldn't not access URL", e);
                }
                catch (IOException e)
                {
                    logger.log(Level.SEVERE, "Problem transferring URL", e);
                }
            }
        });
        pageContentsPoolExecutor.submit(new Runnable()
        {
            public void run()
            {
                try
                {
                    String URLpageContentsJsp = baseURLARender + "getPageContents.jsp?uuid=" + docId + "&pagePosition="
                            + pageNumber;
                    logger.info("Calling : " + URLpageContentsJsp);
                    HttpURLConnection openConnection = (HttpURLConnection) new URL(URLpageContentsJsp).openConnection();
                    if (openConnection.getResponseCode() == 200)
                    {
                        IOUtils.readFully(openConnection.getInputStream(), (int) Long.MAX_VALUE);
                    }
                    else
                    {
                        logger.log(Level.SEVERE, "couldn't not access URL, error code : "
                                + IOUtils.toString(openConnection.getErrorStream()));
                    }
                }
                catch (MalformedURLException e)
                {
                    logger.log(Level.SEVERE, "couldn't not access URL", e);
                }
                catch (IOException e)
                {
                    logger.log(Level.SEVERE, "Problem transferring URL", e);
                }
            }
        });
    }

    private static void parseLayoutForId(final String urlARenderFinal, String docId)
            throws IOException, MalformedURLException
    {
        HttpURLConnection openConnection = (HttpURLConnection) new URL(
                urlARenderFinal + "getDocumentLayout.jsp?uuid=" + docId).openConnection();

        if (openConnection.getResponseCode() == 200)
        {
            String jsonLayout = IOUtils.toString(openConnection.getInputStream());
            JsonObject object = new Gson().fromJson(jsonLayout, JsonObject.class);
            if (object.has("pageDimensionsList"))
            {
                handleJsonDimensions(object, urlARenderFinal);
            }
            else if (object.has("children"))
            {
                resolveChildren(object, urlARenderFinal);
            }
            logger.info("Json parsed for layout: " + object);
        }
        else
        {
            logger.log(Level.SEVERE,
                    "Could not parse layout , error stack: " + IOUtils.toString(openConnection.getErrorStream()));
        }
    }
}
