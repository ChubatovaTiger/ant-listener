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
import org.apache.tools.ant.Project;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class ChangeLogGenerator extends ChangesCollector
{
    private static final String ROWSPAN_PH = "###ROWSPAN###";

    private enum STYLES
    {
        H1, ROW_ODD, ROW_EVEN, ROW_HEADER, A, TABLE, PERF, FIX, FIXES, FIXED, FEATURE, MISC, TESTCASE, TESTCASES, CLEANUP, OTHER
    }

    private static final Map<STYLES, String> STYLE_DEFS = new EnumMap<>( STYLES.class );

    static
    {
        STYLE_DEFS.put( STYLES.H1,
                        "style='text-decoration:none; font-weight:bold; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 18px;'" );
        STYLE_DEFS.put( STYLES.ROW_ODD,
                        "style='background-color: #EEEEEE; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.ROW_EVEN,
                        "style='background-color: #FEFEFE; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.ROW_HEADER,
                        "style='background-color: #77AAFF; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 14px; font-weight: bold;'" );
        STYLE_DEFS.put( STYLES.A,
                        "style='color:#0000FF; text-decoration:none; font-weight:normal; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
//        STYLE_DEFS.put( STYLES.H1, "A.hover{ color:#0000FF; text-decoration:none; font-weight:bold; background-color:#FFFF00; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.TABLE,
                        "style='color:#CCCCC; border-color: #CCCCCC; border-style: solid; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px'" );
        STYLE_DEFS.put( STYLES.PERF,
                        "style='background-color : #D9FFD9; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.FIX,
                        "style='background-color : #E8E8FF; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.FIXES,
                        "style='background-color : #E8E8FF; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.FIXED,
                        "style='background-color : #E8E8FF; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.FEATURE,
                        "style='background-color : #FFE6F2; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.MISC,
                        "style='background-color : #FFFFD7; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.TESTCASE,
                        "style='background-color : #DFFFFF; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.TESTCASES,
                        "style='background-color : #DFFFFF; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.CLEANUP,
                        "style='background-color : #FFE4CA; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
        STYLE_DEFS.put( STYLES.OTHER,
                        "style='background-color : #F2F2F2; border=0; cellspacing=2; cellpadding=2; text-decoration: none; font-family: Verdana, Arial, Helvetica, Geneva, sans-serif;font-size: 12px;'" );
    }

    private String _fileName;

    private String _outputProperty;

    @Override
    public void execute() throws BuildException
    {
        final long start = System.currentTimeMillis();
        final StringBuilder outputContent = new StringBuilder();
        outputContent.append( "<table cellpadding=0 cellspacing=1>" );

        outputContent.append( "<h1 " ).append( getStyle( STYLES.H1 ) ).append( ">Commit Messages</h1>" );
        outputContent.append( "<table " ).append( getStyle( STYLES.TABLE ) ).append(
                "cellpadding=0 cellspacing=1><colgroup><col width=\"600\"><col width=\"202\"></colgroup>" );
        String lastModule = "";
        String author = "";
        String type = "";
        int lineCount = 0;
        final Set<Change> changes = getChanges();
        for ( final Change change : changes )
        {
            final boolean switchedModule = !lastModule.equals( change.getModule() );
            if ( switchedModule )
            {
                outputContent.append( "<tr><td colspan=2 " ).append( getStyle( STYLES.ROW_HEADER ) ).append( ">" );
                outputContent.append( change.getModule() ).append( "</td></tr>" );
                lastModule = change.getModule();
            }
            // if the author changes we need to replace the placeholder for the last line count
            final STYLES style = STYLES.valueOf( change.getType() );
            final boolean switchedAuthor = !author.equals( change.getAuthor() );
            final boolean switchedType = !type.equals( change.getType() );
            if ( switchedModule || switchedAuthor || switchedType )
            {
                final int rowspanPH = outputContent.indexOf( ROWSPAN_PH );
                if ( rowspanPH != -1 )
                {
                    outputContent.replace( rowspanPH, rowspanPH + ROWSPAN_PH.length(), String.valueOf( lineCount ) );
                }
                lineCount = 0;
                author = change.getAuthor();
                type = change.getType();
                outputContent.append( "<tr><td " ).append( getStyle( style ) ).append( ">" );
                outputContent.append( change.getComment() ).append( "</td><td " ).append( getStyle( style ) );
                outputContent.append( " rowspan='" ).append( ROWSPAN_PH ).append( "'>" ).append( change.getAuthor() ).append( "</td></tr>" );
            }
            else
            {
                outputContent.append( "<tr><td " ).append( getStyle( style ) ).append( ">" );
                outputContent.append( change.getComment() ).append( "</td></tr>" );
            }
            lineCount++;
        }
        if ( changes.isEmpty() )
        {
            outputContent.append( "<tr><td colspan=2 " ).append( getStyle( STYLES.OTHER ) ).append( ">No Changes</td><td/></tr>" );
        }
        // cleanup after building the table
        final int rowspanPH = outputContent.indexOf( ROWSPAN_PH );
        if ( rowspanPH != -1 )
        {
            outputContent.replace( rowspanPH, rowspanPH + ROWSPAN_PH.length(), String.valueOf( lineCount ) );
        }
        outputContent.append( "</table>" );

        // the legend
        outputContent.append( "<br><table cellpadding=0 cellspacing=1 " ).append( getStyle( STYLES.TABLE ) );
        outputContent.append( "><colgroup><col span=\"6\" width=\"133\"></colgroup><tr>" );
        outputContent.append( "<td " ).append( getStyle( STYLES.PERF ) ).append( ">Performance</td>" );
        outputContent.append( "<td " ).append( getStyle( STYLES.MISC ) ).append( ">Miscellaneous</td>" );
        outputContent.append( "<td " ).append( getStyle( STYLES.FIX ) ).append( ">Fixed</td>" );
        outputContent.append( "<td " ).append( getStyle( STYLES.TESTCASE ) ).append( ">Testcase</td>" );
        outputContent.append( "<td " ).append( getStyle( STYLES.CLEANUP ) ).append( ">Cleanup</td>" );
        outputContent.append( "<td " ).append( getStyle( STYLES.FEATURE ) ).append( ">Feature</td>" );
        outputContent.append( "</tr></table><br>" );

        outputContent.append( "<h1 " ).append( getStyle( STYLES.H1 ) ).append( ">Changed Files</h1><table cellpadding=0 cellspacing=1 " );
        outputContent.append( getStyle( STYLES.TABLE ) ).append( "><colgroup><col width=\"600\"><col width=\"50\"><col width=\"152\"></colgroup>" );
        lastModule = "";
        int count = 0;
        final Set<ChangedFile> changedFiles = getChangedFiles();
        for ( final ChangedFile changedFile : changedFiles )
        {
            if ( !lastModule.equals( changedFile.getModule() ) )
            {
                outputContent.append( "<tr><td colspan=3 " ).append( getStyle( STYLES.ROW_HEADER ) ).append( ">" );
                outputContent.append( changedFile.getModule() ).append( "</td></tr>" );
                lastModule = changedFile.getModule();
                count = 0;
            }
            final String style = getStyle( count++ % 2 == 0 ? STYLES.ROW_EVEN : STYLES.ROW_ODD );
            outputContent.append( "<tr><td " ).append( style ).append( "><a " ).append( getStyle( STYLES.A ) ).append( " href='" );
            outputContent.append( changedFile.getBrowseURL() );
            outputContent.append( "?r=" ).append( changedFile.getRevision() ).append( "'>" ).append( changedFile.getFileName( true ) );
            outputContent.append( "</a></td><td " ).append( style ).append( ">" ).append( changedFile.getRevision() );
            outputContent.append( "</td><td " ).append( style ).append( ">" );
            outputContent.append( changedFile.getAuthor() ).append( "</td></tr>" );
        }
        if ( changedFiles.isEmpty() )
        {
            outputContent.append( "<tr><td colspan=3 " ).append( getStyle( STYLES.ROW_EVEN ) ).append( ">No Changes</td><td/></tr>" );
        }
        outputContent.append( "</table></table>" );
        // write output file if necessary
        if ( _fileName != null && !_fileName.isEmpty() )
        {
            try (final Writer fout = new FileWriter( _fileName );
                 final BufferedWriter out = new BufferedWriter( fout ))
            {
                out.write( "<html>" + outputContent.toString() + "</html>" );
            }
            catch ( final IOException e )
            {
                log( "Failed to write to output file." );
            }
        }
        if ( _outputProperty != null )
        {
            getProject().setNewProperty( _outputProperty, outputContent.toString() );
        }
        if ( !EXECUTOR_SERVICE.isTerminated() )
        {
            EXECUTOR_SERVICE.shutdown();
            log( "Shutting down executor service.", Project.MSG_INFO );
        }

        final double duration = ( double ) Math.round( ( System.currentTimeMillis() - start ) / 1000.0 * 100 ) / 100.0;
        log( "Finished changelog generation (" + duration + " sec, " + outputContent.toString().getBytes().length + " bytes read)", Project.MSG_INFO );
    }

    private String getStyle( final STYLES style )
    {
        return STYLE_DEFS.get( style );
    }

    public void setFileName( final String fileName )
    {
        _fileName = fileName;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setOutputProperty( final String outputProperty )
    {
        _outputProperty = outputProperty;
    }

    public static void main( final String[] args )
    {
        final ChangeLogGenerator generator = new ChangeLogGenerator();
        generator.setFileName( "ChangeLog.html" );
        generator.setPreviousTag( "OVAP-DailyBuild-14-0-0-2584" );
        generator.setCurrentTag( "OVAP-DailyBuild-14-0-0-2588" );
        generator.execute();
    }
}
