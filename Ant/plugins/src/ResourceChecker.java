import org.apache.tools.ant.Task;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Ant task to check the existence of the resouce _files
 */
public class ResourceChecker extends Task
{
    private String _resourceDir = ".";

    private String _webInfDir = ".";

    private String _outputDir = ".";

    private final String[] _files = {"Disconnect.vxml", "Exit.vxml", "Overflow.vxml", "Redirect.vxml", "Return.vxml", "Unavailable.vxml"};

    private static final String OUTPUT_FILENAME = "ResourceCheckReport.html";

    private static final String STYLE = "<style>\n" + "table.width100          { width: 100%; border: solid 1px #000000; }\n" +
            "td.menu                 { background-color: #888888; color: #FFFFFF; text-align: center; padding: 1px; font-family: Verdana, Arial, Helvetica, sans-serif; font-size: 10pt; font-weight: bold;}\n" +
            "td                      { font-family: Verdana, Arial, Helvetica, sans-serif; font-size: 10pt; padding: 4px; text-align: left; }\n" +
            "p.form-title              { background-color: #ffffff; color: #000000; font-weight: bold; text-align: left; font-family: Verdana, Arial, Helvetica, sans-serif;}\n" +
            "</style>";

    private static final String HEADER = "<tr><td class=\"menu\">Language</td><td class=\"menu\">Path</td><td class=\"menu\">Error</td></tr>";


    public ResourceChecker()
    {
    }

    /**
     * 
     */
    @Override
    public void execute()
    {
        final Map missingDirMap = new HashMap();
        final Map missingFileMap = new HashMap();
        try
        {
            // read the driver file
            final String driverFile1 = "MPDrivers.xml";
            final String configDir = "config";
            final String driverFile = new File( new File( _webInfDir, configDir ).getPath(), driverFile1 ).getPath();

            final String resourceParentDir = "System\\VXML";
            final String resourcePath = new File( _resourceDir, resourceParentDir ).getPath();

            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = builderFactory.newDocumentBuilder();
            final InputStream stream = new FileInputStream( driverFile );
            final Document doc = builder.parse( stream );
            final List drivers = getChildsByName( doc.getDocumentElement(), "mp" );

            for ( int i = 0; i < drivers.size(); i++ )
            {
                final Node driver = ( Node ) drivers.get( i );
                final Node driverExternalNameNode = getFirstChildByName( ( Element ) driver, "externalName" );
                final Node driverInternalNameNode = getFirstChildByName( ( Element ) driver, "internalName" );
                final String internalName = getNodeText( driverInternalNameNode );
                final String externalName = getNodeText( driverExternalNameNode );

                final List languages = getChildsByName( ( Element ) getFirstChildByName( ( Element ) driver, "languages" ), "key" );

                for ( int j = 0; j < languages.size(); j++ )
                {
                    final Node languageNode = ( Node ) languages.get( j );
                    final String language = getAttributeValue( ( Element ) languageNode, "voName" );
                    if ( ! "Default".equalsIgnoreCase( language ) )
                    {
                        // build the path .../Resources/System/VXML/<LANGUAGE>/dialogs/VXML
                        final String resourceSubDir = "dialogs\\VXML";
                        final String path = new File ( new File( resourcePath, language ).getPath(), resourceSubDir ).getPath();
                        final String tmpDriver = internalName.replace( '.', File.separatorChar );
                        final File targetDir = new File( path, tmpDriver );
                        // check the directory first
                        if ( targetDir.exists() )
                        {
                            // now check the files
                            for ( int k = 0; k < _files.length; k++ )
                            {
                                final File targetFile = new File( targetDir, _files[k] );
                                if ( !targetFile.exists() )
                                {
                                    fillDetails( missingFileMap, externalName, language, targetFile );
                                }
                            }
                        }
                        else
                        {
                            fillDetails( missingDirMap, externalName, language, targetDir );
                        }
                    }
                }
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        generateOutput( missingDirMap, missingFileMap );
    }

    private static void fillDetails( final Map missingDirMap, final String externalName, final String language, final File targetDir )
    {
        Map missingDetails = ( Map ) missingDirMap.get( externalName );
        if ( missingDetails == null )
        {
            missingDetails = new HashMap();
            missingDirMap.put( externalName, missingDetails );
        }
        missingDetails.put( language, targetDir );
    }

    private void generateOutput( final Map missingDirMap, final Map missingFileMap )
    {
        final StringBuffer html = new StringBuffer();
        final List processedDriver = new ArrayList();
        html.append( "<html><head>" ).append( STYLE ).append( "<title>Resouce File Check</title></head><body>" );
        if ( missingDirMap.isEmpty() )
        {
            html.append( "<p class=\"form-title\">No directories missing.</p>" );
        }
        else
        {
            Object[] keys = missingDirMap.keySet().toArray();
            for ( int i = 0; i < keys.length; i++ )
            {
                final Object driver = keys[i];
                processedDriver.add( driver );
                html.append( "<p class=\"form-title\">" ).append( "Driver " ).append( driver ).append( "</p>" );
                Map details = ( Map ) missingDirMap.get( driver );
                final Object[] missingDirs = details.keySet().toArray();
                html.append( "<table class=\"width100\">" );
                html.append( HEADER );
                writeErrors( missingDirs, html, details, "Directory missing" );
                details = ( Map ) missingFileMap.get( driver );
                if ( details != null )
                {
                    final Object[] missingFiles = details.keySet().toArray();
                    writeErrors( missingFiles, html, details, "File missing" );
                }
                html.append( "</table>" );
            }
            keys = missingFileMap.keySet().toArray();
            for ( int i = 0; i < keys.length; i++ )
            {
                final Object driver = keys[i];
                if ( processedDriver.contains( driver ) ) continue;
                html.append( "<p class=\"form-title\">" ).append( "Driver " ).append( driver ).append( "</p>" );
                html.append( "<table class=\"width100\">" );
                html.append( HEADER );
                final Map details = ( Map ) missingFileMap.get( driver );
                if ( details != null )
                {
                    final Object[] missingFiles = details.keySet().toArray();
                    writeErrors( missingFiles, html, details, "File missing" );
                }
                html.append( "</table>" );
            }
        }
        html.append( "</body></html>" );
        try ( final FileWriter writer = new FileWriter( new File( _outputDir, OUTPUT_FILENAME ) ) )
        {
            writer.write( html.toString() );
            writer.flush();
        }
        catch ( Exception e )
        {
            e.printStackTrace( );
        }
    }

    private void writeErrors( final Object[] missingDirs, final StringBuffer html, final Map details, final String msg )
    {
        for ( int j = 0; j < missingDirs.length; j++ )
        {
            final String language = ( String ) missingDirs[j];
            html.append( "<tr><td>" ).append( language ).append( "</td><td>" );
            html.append( details.get( language ) ).append( "</td><td>" );
            html.append( msg ).append( "</td></tr>" );
        }
    }

    /**
     * @param elementNode Element node
     * @param attrName Name of the attribute to get the value from
     * @return String if the attribute has value otherwise returns null
     */
    public static String getAttributeValue( final Element elementNode, final String attrName )
    {
        if ( null == elementNode ) return null;
        if ( !elementNode.hasAttribute( attrName ) ) return null;
        return elementNode.getAttribute( attrName );
    }

    private static String getNodeText( final Node node )
    {
        if ( null == node ) return null;

        if ( !node.hasChildNodes() ) return "";

        final NodeList children = node.getChildNodes();
        final StringBuilder nodeText = new StringBuilder();

        for ( int i = 0; i < children.getLength(); i++ )
        {
            final Node child = children.item( i );

            final short nodeType = child.getNodeType();
            if ( Node.TEXT_NODE == nodeType || Node.CDATA_SECTION_NODE == nodeType )
            {
                nodeText.append( child.getNodeValue() );
            }
        }

        return nodeText.toString();
    }

    private static Node getFirstChildByName( final Element element, final String name )
    {
        return ( Node ) getChildsByName( element, name, true );
    }

    private static List getChildsByName( final Element element, final String name )
    {
        return ( List ) getChildsByName( element, name, false );
    }

    /**
     * Returns children for the Element with the specified name.
     *
     * @param element whose children are to be checked.
     * @param name name of the child to be searched in the parent node.
     * @return List consisting of all the children with said name if present, else otherwise an empty arraylist.
     */
    private static Object getChildsByName( final Element element, final String name, final boolean firstOnly )
    {
        final List childList = new ArrayList();
        if ( null != element )
        {
            final NodeList nList = element.getChildNodes();
            final int listSize = nList.getLength();
            for ( int i = 0; i < listSize; i++ )
            {
                final Node n = nList.item( i );
                if ( Node.ELEMENT_NODE == n.getNodeType() )
                {
                    if ( name.equals( n.getNodeName() ) )
                    {
                        childList.add( n );
                        if ( firstOnly ) return n;
                    }
                }
            }
        }
        return childList;
    }

    public static void main(final String[] args)
    {
        final ResourceChecker checker = new ResourceChecker();
        final long start = System.currentTimeMillis();
        checker.setWebInfDir( "D:\\cvslocal\\OVAP\\1.0\\Development\\OneBridge\\WEB-INF" );
        checker.setResourceDir( "D:\\cvslocal\\OVAP\\1.0\\Development\\OneBridge\\Resources" );
        checker.setOutputDir( "D:\\tmp\\" );
        checker.execute();
        System.out.println("Finished resource check after " + (System.currentTimeMillis() - start ) / 1000 + " seconds.");
    }

    /**
     * @return Returns the path.
     */
    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public String getResourceDir()
    {
        return _resourceDir;
    }

    /**
     * @param resourceDir
     *            The path to set.
     */
    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setResourceDir(final String resourceDir )
    {
        _resourceDir = resourceDir;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public String getWebInfDir()
    {
        return _webInfDir;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setWebInfDir( final String webInfDir )
    {
        _webInfDir = webInfDir;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public String getOutputDir()
    {
        return _outputDir;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setOutputDir( final String outputDir )
    {
        _outputDir = outputDir;
    }
}
