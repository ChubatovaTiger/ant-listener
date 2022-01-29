/*
 * $RCSfile$
 *
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2002-2007 VoiceObjects. All Rights Reserved. Confidential.
 */

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DynPropsVersionTagger extends Task
{
    private String _versionNumber;

    private String _currentFile;

    private String _previousFile;

    @Override
    public void execute() throws BuildException
    {
        final String newFile;
        final String oldFile;
        try
        {
            newFile = readFileAsString( _currentFile );
            oldFile = readFileAsString( _previousFile );
        }
        catch ( IOException e )
        {
            throw new BuildException( "Failed to read file.", e );
        }

        // grep the old version number, assume that it was not changed manually in the newFile
        final Document newDoc;
        final Document oldDoc;
        try
        {
            final DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            newDoc = buildDocument( newFile, docBuilder );
            oldDoc = buildDocument( oldFile, docBuilder );

            // read the version number from the old document
            final NodeList nl = oldDoc.getElementsByTagName( "statistics" );
            if ( nl.getLength() != 1 )
            {
                throw new IllegalStateException( "There must be exactly one statistics element below root but found " + nl.getLength() + '.' );
            }
            final Node versionNode = nl.item( 0 );
            _versionNumber = ( ( Element ) versionNode ).getAttribute( "version" );
            if ( _versionNumber == null || _versionNumber.isEmpty() ) _versionNumber = "1.0";
        }
        catch ( Exception e )
        {
            throw new BuildException( "Failed to extract the version number.", e );
        }

        // now do the compare
        try
        {
            final List<String> newNodes = retrieveDocumentContent( newDoc );
            final List<String> oldNodes = retrieveDocumentContent( oldDoc );

            // if the number of nodes changed we had a difference
            boolean versionChanged = newNodes.size() != oldNodes.size();

            if ( !versionChanged )
            {
                // compare the content, if we have two equally sized lists the the content is not the same we should have retained values here
                newNodes.removeAll( oldNodes );
                versionChanged = !newNodes.isEmpty();
            }

            if ( versionChanged )
            {
                // increment the version number
                final String minorVersion = _versionNumber.substring( _versionNumber.indexOf( '.' ) + 1 );
                final String majorVersion = _versionNumber.substring( 0, _versionNumber.indexOf( '.' ) );
                _versionNumber = majorVersion + '.' + (Integer.parseInt( minorVersion ) + 1);
                getProject().setNewProperty("DynPropVersionNumber", _versionNumber);
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            throw new BuildException( "Failed to compare xml documents.", e );   
        }
    }

    private List<String> retrieveDocumentContent( final Document newDoc )
    {
        final Queue<Node> nodes = new LinkedList<>();
        final List<String> visitedNodes = new ArrayList<>();
        addNodes( newDoc.getDocumentElement().getChildNodes(), nodes );
        while ( !nodes.isEmpty() )
        {
            final Node node = nodes.remove();
            switch ( node.getNodeType() )
            {
                case Node.ELEMENT_NODE:
                    visitedNodes.add( getNodePath( node ) );
                    addNodes( node.getChildNodes(), nodes );
                    break;
            }
        }
        return visitedNodes;
    }

    private String getNodePath( final Node node )
    {
        Node currentNode = node;
        String path = getNodeIdentifier( node );
        while ( currentNode.getParentNode() != null )
        {
            currentNode = currentNode.getParentNode();
            final String currentPart = getNodeIdentifier( currentNode );
            path = String.format( "%s/%s", currentPart, path );
        }
        return path;
    }

    private String getNodeIdentifier( final Node node )
    {
        String identifier = node.getNodeName();
        final NamedNodeMap nmap = node.getAttributes();
        if ( nmap != null )
        {
            for ( int i = 0; i < nmap.getLength(); i++ )
            {
                final Node attrNode = nmap.item( i );
                identifier = String.format( "%s@@%s=%s", identifier, attrNode.getNodeName(), attrNode.getNodeValue() );
            }
        }
        return identifier;
    }

    private void addNodes( final NodeList childNodes, final Queue<Node> nodeList )
    {
        for ( int i = 0; i < childNodes.getLength(); i++ )
        {
            final Node node = childNodes.item( i );
            if ( !nodeList.contains( node ) ) nodeList.offer( node );
        }
    }

    private Document buildDocument( final String newFile, final DocumentBuilder docBuilder )
            throws SAXException, IOException
    {
        final StringReader sr = new StringReader( newFile );
        final InputSource is = new InputSource( sr );
        return docBuilder.parse( is );
    }

    @SuppressWarnings( {"ResultOfMethodCallIgnored"} )
    private static String readFileAsString( final String filePath ) throws IOException
    {
        final byte[] buffer = new byte[( int ) new File( filePath ).length()];

        try ( final BufferedInputStream f = new BufferedInputStream( new FileInputStream( filePath ) ) )
        {
            f.read( buffer );
        }
        return new String( buffer );
    }

    public void setPreviousFile( final String fileName )
    {
        _previousFile = fileName;
    }

    public void setCurrentFile( final String fileName )
    {
        _currentFile = fileName;
    }

    public String getVersionNumber()
    {
        return _versionNumber;
    }

    public static void main( final String[] args )
    {
        final DynPropsVersionTagger tagger = new DynPropsVersionTagger();
        tagger.setCurrentFile( "C:\\cvslocal\\OVAP\\1.0\\Development\\OneBridge\\WEB-INF\\config\\DynamicProperties.xml" );
        tagger.setPreviousFile( "C:\\LocalRepository\\scratchpad\\resources\\DynamicProperties.xml" );
        tagger.execute();
        System.out.println( tagger.getVersionNumber() );
    }
}
