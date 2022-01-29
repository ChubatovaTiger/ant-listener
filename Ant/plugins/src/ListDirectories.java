import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Stack;

public class ListDirectories extends Task
{
    public File _directory;

    private File _output;

    private boolean _verbose;

    private static final String INDENT = "   ";

    private static final String FILE_LEVEL_1 = "...";

    private static final String DIR_LEVEL_1 = "|---";

    private static final String[] UNIT = {"B ", "KB", "MB", "GB", "TB", "PB"};

    public void setDirectory( final File directory )
    {
        _directory = directory;
    }

    public void setOutput( final File output )
    {
        _output = output;
    }

    public void setVerbose( final boolean verbose )
    {
        _verbose = verbose;
    }

    @Override
    public void execute()
    {
        if ( _directory.isDirectory() )
        {
            if ( _verbose ) log( "Opening output file " + _output.getAbsolutePath() );
            try (FileWriter writer = new FileWriter( _output, false ))
            {
                if ( _verbose ) log( "Generating listing for " + _directory.getAbsolutePath() );
                final Stack<DirEntry> queue = new Stack<>();
                queue.push( new DirEntry( _directory, "|" ) );
                while ( !queue.isEmpty() )
                {
                    final DirEntry entry = queue.pop();
                    final File dir = entry.getDir();
                    final String indent = entry.getIndent();
                    if ( _verbose ) log( "Listing directory " + dir.getAbsolutePath() );
                    writeEntry( writer, dir, indent );
                    final File[] dirs = sortFiles( dir.listFiles() );
                    if ( dirs != null )
                    {
                        for ( final File file : dirs )
                        {
                            final String newIndent = indent + INDENT + ( dirs.length > 1 ? "|" : " " );
                            queue.push( new DirEntry( file, newIndent ) );
                        }
                    }
                }

                writer.write( "\nTotal space: " + convertSizeFromBytes( _directory.getTotalSpace() ) + "\n" );
                writer.write( "Usable space: " + convertSizeFromBytes( _directory.getUsableSpace() ) + "\n" );
                writer.write( "Free space: " + convertSizeFromBytes( _directory.getFreeSpace() ) + "\n" );
            }
            catch ( final Exception e )
            {
                throw new BuildException( e );
            }
        }
    }

    private void writeEntry( final FileWriter writer, final File dir, final String indent ) throws IOException
    {
        final String permissions = ( dir.canRead() ? "r" : " " ) + ( dir.canWrite() ? "w" : " " );
        final String output;
        if ( dir.isDirectory() )
        {
            output = String.format( "%s%1td.%1tm.%1tY %1tT    %s    %s    %s\n", space( dir.isDirectory(), indent ),
                                    dir.lastModified(), dir.lastModified(), dir.lastModified(), dir.lastModified(),
                                    getOwner(dir), permissions, dir.getName() );
        }
        else
        {
            output = String.format( "%s%1td.%1tm.%1tY %1tT    %10s    %s    %s    %s\n", space( dir.isDirectory(), indent ),
                                    dir.lastModified(), dir.lastModified(), dir.lastModified(), dir.lastModified(),
                                    convertSizeFromBytes( dir.length() ), getOwner(dir), permissions, dir.getName() );
        }
        writer.write( output );
    }

    private String getOwner( final File file )
    {
        try
        {
            return Files.getOwner( file.toPath() ).getName();
        }
        catch ( final IOException e )
        {
            return "n/a";
        }
    }

    private String space( final boolean isDirectory, final String indent )
    {
        return ( isDirectory ? indent.substring( 0, indent.length() - 1 ) + DIR_LEVEL_1 : indent + FILE_LEVEL_1 );
    }

    private File[] sortFiles( final File[] files )
    {
        if ( files == null ) return null;
        Arrays.sort( files, new Comparator<File>()
        {
            @Override
            public int compare( final File file1, final File file2 )
            {
                return -1 * file1.getName().compareToIgnoreCase( file2.getName() );
            }
        } );
        return files;
    }

    public static String convertSizeFromBytes( final long sizeInBytes )
    {
        int i;
        double dSize = sizeInBytes;
        for ( i = 0; i < UNIT.length && 1024 < dSize; i++ )
        {
            dSize /= 1024;
        }

        if ( i == UNIT.length )
        {
            i--;
            dSize *= 1024;
        }

        return String.format( "%.2f " + UNIT[i], dSize );
    }

    public static void main( final String[] args )
    {
        final ListDirectories ld = new ListDirectories();
        ld.setDirectory( new File( "." ) );
        ld.setOutput( new File( "listDir.txt" ) );
        ld.setVerbose( true );
        ld.execute();
    }

    private static class DirEntry
    {
        private final File _dir;

        private final String _indent;

        public DirEntry( final File dir, final String indent )
        {
            _dir = dir;
            _indent = indent;
        }

        public File getDir()
        {
            return _dir;
        }

        public String getIndent()
        {
            return _indent;
        }
    }
}