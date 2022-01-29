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
import org.apache.tools.ant.Task;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.ScriptingContainer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ChefTask extends Task
{
    private int MAX_BACKUP_FILES = 5;

    public class Entry
    {
        private String template;

        private String destination;

        public Entry()
        {
        }

        public void setTemplate( String a )
        {
            this.template = a;
        }

        public void setDestination( String a )
        {
            this.destination = a;
        }

        public String getDestination()
        {
            return destination;
        }

        public String getTemplate()
        {
            return template;
        }
    }

    public class MiniChef
    {

        private String jChefHome;

        private String preambleFile;

        private String defaultFile;

        private String userConfig;

        private String variableFile;

        private String finaleFile;

        private String destFolder;

        private String chefHome;

        private int keepBackup;

        @SuppressWarnings( "unused" )
        public MiniChef()
        {
        }

        private String joinPath( String basePath, String path, String def )
        {
            String t = joinPath( basePath, path );
            if ( t == null || t.isEmpty() )
            {
                return def;
            }
            return t;
        }

        private String joinPath( String basePath, String path )
        {
            if ( basePath == null )
            {
                return path;
            }

            if ( basePath.endsWith( "/" ) )
            {
                basePath = basePath.substring( 0, basePath.length() - 1 );
            }

            if ( path == null )
            {
                return basePath;
            }

            if ( path.startsWith( "/" ) )
            {
                return path;
            }

            return basePath + "/" + path;
        }

        private String lastPart( String input )
        {
            int slash = input.lastIndexOf( "/" );
            if ( slash >= 0 )
            {
                return input.substring( slash + ( slash < input.length() ? 1 : 0 ) );
            }
            return input;
        }

        public MiniChef( String chefHome, String defaultFile, String userConfig, String jChefHome, String preambleFile, String variableFile, String finaleFile, String destFolder, int keepBackup )
        {

            this.chefHome = joinPath( chefHome, null, "." );        // where to find chef
            this.defaultFile = joinPath( chefHome, defaultFile );   // file to read the default[] variables from

            this.jChefHome = joinPath( jChefHome, null, "." );      // home of jChef (somewhere in Build/resources)
            this.userConfig = joinPath( userConfig, null, "." );    // user config overriding defaults
            this.preambleFile = joinPath( jChefHome, preambleFile );// ruby class to hold node/defaults
            this.finaleFile = joinPath( jChefHome, finaleFile );    // applying the template
            this.variableFile = joinPath( jChefHome, variableFile );// mapping from variables to node/defaults
            this.destFolder = joinPath( destFolder, null, "." );    // where to write the output into

            this.keepBackup = keepBackup;
            MAX_BACKUP_FILES = keepBackup;
        }

        public void process( ArrayList<Entry> entries )
        {
            try
            {

                ScriptingContainer container = new ScriptingContainer( LocalVariableBehavior.PERSISTENT );

                // tell jruby where to find the ruby stuff
                //log( "local directory " + Paths.get( "." ).toAbsolutePath().normalize().toString() );
                HashMap<String, String> envmap = new HashMap<>();
                envmap.put( "RUBYLIB", jChefHome );
                container.setEnvironment( envmap );

                container.runScriptlet( "latest_build_version=\"localBuild\"" );
                container.runScriptlet( "latest_build_number=\"0\"" );

                log( "running " + preambleFile, Project.MSG_INFO );
                // prepare the node-object
                InputStream ip = new FileInputStream( new File( preambleFile ) );
                container.runScriptlet( ip, preambleFile );
                ip.close();

                // 2. step: read defaults.rb, should be global for all, also read userConfig.json and put it in
                log( "reading in defaults " + defaultFile, Project.MSG_DEBUG );
                InputStream i = new FileInputStream( new File( defaultFile ) );
                container.runScriptlet( i, defaultFile );
                i.close();

                Object defaultsNode = container.get( "node" );

                String userData;

                if ( userConfig != null )
                {
                    // read user file
                    userData = Files.lines( Paths.get( userConfig ) )
                                    .parallel() // for parallel processing
                                    .map( String::trim ) // to change line
                                    .collect( Collectors.joining() );
                }
                else
                {
                    userData = "{}";
                }
                container.put( "userJson", userData );
                // 3. step: iterate through "process"-list from the config file,
                //      temporarily add configUser-file (json->hash) to node from default and
                //      erubi the template, saving as destination-file

                entries.forEach( o ->
                                 {

                                     String templateFile = chefHome + "/" + o.getTemplate();
                                     String destinationFile = destFolder + "/" + o.getDestination();

                                     //System.out.println("processing " + templateFile + " -> " + destinationFile);
                                     log( "processing " + lastPart( templateFile ) + " -> " + lastPart( destinationFile ), Project.MSG_INFO );
                                     container.put( "node", defaultsNode ); // reset node data

                                     String result = null; // to join lines
                                     try
                                     {

                                         // run variable substitution
                                         if ( new File( variableFile ).exists() )
                                         {
                                             log( "patching with " + variableFile, Project.MSG_DEBUG );
                                             InputStream isf = new FileInputStream( new File( variableFile ) );
                                             container.runScriptlet( isf, variableFile );
                                             isf.close();
                                         }
                                         // read template
                                         result = Files.lines( Paths.get( templateFile ) )
                                                       .collect( Collectors.joining( "\n" ) );

                                         container.put( "template", result );
                                         container.put( "templatename", lastPart( templateFile ) );

                                         // run templateierer
                                         InputStream isf = new FileInputStream( new File( finaleFile ) );
                                         container.runScriptlet( isf, finaleFile );
                                         isf.close();

                                         //  get and write result to file
                                         String resultingData = ( String ) container.get( "binding" );
                                         // we want backup? try to reanem & delete old backup files
                                         if ( keepBackup > 0 && Paths.get( destinationFile ).toFile().exists() )
                                         {
                                             int number = 0;
                                             String newName = destinationFile + "." + number + ".bak";
                                             while ( Paths.get( newName ).toFile().exists() && number < MAX_BACKUP_FILES )
                                             {
                                                 newName = destinationFile + "." + ++number + ".bak";
                                             }
                                             if ( number == MAX_BACKUP_FILES )
                                             {
                                                 for ( int j = MAX_BACKUP_FILES - 1; j >= 0; j-- )
                                                 {
                                                     boolean success = Paths.get( destinationFile + "." + j + ".bak" ).toFile().renameTo( new File( destinationFile + "." + ( j + 1 ) + ".bak" ) );
                                                     if ( !success )
                                                     {
                                                         log( "failed to rename backup-file: " + destinationFile + "." + j + ".bak to " + destinationFile + "." + ( j + 1 ) + ".bak" );
                                                     }
                                                 }
                                                 newName = destinationFile + ".0.bak";
                                             }
                                             boolean success = Paths.get( destinationFile ).toFile().renameTo( new File( newName ) );
                                             if ( !success )
                                             {
                                                 log( "failed to rename " + destinationFile + " to " + newName );
                                             }
                                         }

                                         // now write the file
                                         try (BufferedWriter writer = Files.newBufferedWriter( Paths.get( destinationFile ) ))
                                         {
                                             writer.write( resultingData );
                                         }
                                         container.put( "binding", "" );
                                     }
                                     catch ( IOException e )
                                     {
                                         e.printStackTrace();
                                     }
                                 } );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
    }

    final private ArrayList<Entry> conversionEntries = new ArrayList<>();

    // jChefHome points per default to ${LocalEnv.ANT_ROOT}/../1.0/Build/resources/jchef
    private String jchefHome = null;

    private String preambleFile = "preamble.rb";

    private String variableFile = "variables.rb";

    private String finaleFile = "final.rb";

    // chefHome points par default to ${LocalEnv.ANT_ROOT}/../../chef/
    private String chefHome = null;

    private String defaultFile = "attributes/default.rb";

    private String userConfig = null;

    private String destFolder = null;

    private int keepBackup = 5;

    public void setChefhome( String filename )
    {
        this.chefHome = filename;
    }

    public void setDefaultsfile( String filename )
    {
        this.defaultFile = filename;
    }

    public void setJchefhome( String filename )
    {
        this.jchefHome = filename;
    }

    public void setUserconfig( String filename )
    {
        this.userConfig = filename;
    }

    public void setPraeamblefile( String filename )
    {
        this.preambleFile = filename;
    }

    public void setVariableFile( String filename )
    {
        this.variableFile = filename;
    }

    public void setFinalefile( String filename )
    {
        this.finaleFile = filename;
    }

    public void setDestFolder( String filename )
    {
        this.destFolder = filename;
    }

    public void setKeepBackup( int b )
    {
        this.keepBackup = b;
    }

    public Entry createEntry()
    {
        Entry entry = new Entry();
        conversionEntries.add( entry );
        return entry;
    }

    public void execute() throws BuildException
    {
        log( "performing chef tasks.." );
        if ( this.destFolder == null || this.chefHome == null || this.jchefHome == null )
        {
            log( "Cannot start, cannot find all needed files." );
        }
        MiniChef cheffchen = new MiniChef(
                this.chefHome,
                this.defaultFile,
                this.userConfig,
                this.jchefHome,
                this.preambleFile,
                this.variableFile,
                this.finaleFile,
                this.destFolder,
                this.keepBackup );
        cheffchen.process( conversionEntries );
    }
}
