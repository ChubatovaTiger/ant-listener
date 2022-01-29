/*
 * $RCSfile$
 *
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2002-2007 VoiceObjects. All Rights Reserved. Confidential.
 */

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChangesCollector extends Task
{
    protected static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private static final Pattern COMMIT_TYPE = Pattern.compile( "\\s*\\[(PERF|MISC|FIX|FIXES|FIXED|TESTCASE|TESTCASES|FEATURE|CLEANUP)\\].*",
                                                                Pattern.CASE_INSENSITIVE );

    private static final String MODULE_PH = "%%%MODULE%%%";

    private static final String PREVIOUS_TAG_PH = "%%%PREVIOUS_TAG%%%";

    private static final String CURRENT_TAG_PH = "%%%CURRENT_TAG%%%";

    private static final String TERM_SEQ = "\"\r\n";

    private String _previousTag;

    protected String _currentTag;

    private String _host = "fishbowl.eng.voxeo.com";

    private String _path = "/search";

    private String _contentRoot = "CologneCVS";

    private String[] _modules = new String[]{"OVAP/1.0/Development/OneBridge/WEB-INF",
                                             "OVAP/1.0/Development/OneBridge/Desktop",
                                             "OVAP/1.0/Development/OneBridge/WebCommander",
                                             "OVAP/1.0/Development/OneBridge/BUI",
                                             "OVAP/1.0/Development/OneBridge/Studio"};

    private String _query = "select revisions from dir \"" + MODULE_PH + "\" where on branch MAIN and after tag \"" + PREVIOUS_TAG_PH +
            "\" order by date desc group by changeset return path, revision, author, comment";

    private String _queryParam = "ql=";

    private String _csvParam = "csv=true";

    private String _messageIgnorePattern = "Generated during build \\d{4}";

    private String _additionalParams;

    private final TreeSet<Change> _changes = new TreeSet<>();

    private final TreeSet<ChangedFile> _changedFiles = new TreeSet<>();

    private boolean _queryPerformed;

    private long _timeoutInMinutes = 5;

    protected Set<Change> getChanges()
    {
        if ( !_queryPerformed ) queryChanges();
        return _changes;
    }

    protected Set<ChangedFile> getChangedFiles()
    {
        if ( !_queryPerformed ) queryChanges();
        return _changedFiles;
    }

    private void queryChanges()
    {
        if ( _queryPerformed ) return;
        Pattern pattern = null;
        if ( _messageIgnorePattern != null )
        {
            try
            {
                pattern = Pattern.compile( _messageIgnorePattern );
            }
            catch ( final Exception e )
            {
                log( "Failed to compile message ignore pattern '" + _messageIgnorePattern + "':" + e.getMessage() );
                pattern = null;
            }
        }

        final List<FutureWrapper> list = new ArrayList<>();
        for ( final String module : _modules )
        {

            final String url = buildUrl( module );
            final Future<ChangeSet> result = EXECUTOR_SERVICE.submit( new ChangeLogFetcherTask( url, module, pattern ) );
            list.add( new FutureWrapper( result, url ) );
        }

        for ( final FutureWrapper wrapper : list )
        {
            try
            {
                final ChangeSet fisheyeResult = wrapper.getFuture().get( _timeoutInMinutes, TimeUnit.MINUTES );
                _changedFiles.addAll( fisheyeResult.getChangedFiles() );
                _changes.addAll( fisheyeResult.getChanges() );
                _queryPerformed = true;
            }
            catch ( final InterruptedException e )
            {
                log( "InterruptedException: Failed to fetch changelog from  " + wrapper.getUrl(), Project.MSG_ERR );
            }
            catch ( final ExecutionException e )
            {
                log( "ExecutionException: Failed to fetch changelog from " + wrapper.getUrl(), e, Project.MSG_ERR );
            }
            catch ( final TimeoutException e )
            {
                log( "TimeoutException: Failed to fetch changelog from " + wrapper.getUrl(), Project.MSG_ERR );
            }
        }
    }

    private String extractVersion( final String inputLine )
    {
        // field 1 should be the filename
        return extractField( inputLine, 1 );
    }

    private String extractAuthor( final String inputLine )
    {
        // field 1 should be the filename
        return extractField( inputLine, 2 );
    }

    private String extractComment( final String inputLine )
    {
        // field 1 should be the filename
        return extractField( inputLine, 3 );
    }

    private String extractFileName( final String inputLine )
    {
        // field 0 should be the filename
        final String file = extractField( inputLine, 0 );
        return file.substring( file.lastIndexOf( '/' ) + 1 );
    }

    private String extractPath( final String inputLine )
    {
        // field 0 should be the filename
        final String file = extractField( inputLine, 0 );
        return file.substring( 0, file.lastIndexOf( '/' ) );
    }

    private String extractField( final String inputLine, final int field )
    {
        if ( inputLine != null )
        {
            final String[] parts = inputLine.split( "," );
            if ( parts.length > field )
            {
                return parts[field].replaceAll( "\"", "" );
            }
        }
        return "";
    }

    private String buildUrl( final String module )
    {
        final String query = _query.replaceAll( MODULE_PH, module ).replaceAll( PREVIOUS_TAG_PH, _previousTag ).replaceAll( CURRENT_TAG_PH,
                                                                                                                            _currentTag );
        try
        {
            final String queryParam = _queryParam + URLEncoder.encode( query, "UTF-8" );
            final String url = "http://" + _host + _path + '/' + _contentRoot + '?';
            final String params = queryParam + '&' + _csvParam + "&" + _additionalParams;
            return url + params;
        }
        catch ( final UnsupportedEncodingException e )
        {
            throw new IllegalStateException( "Unsupported encoding UTF-8.", e );
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setPreviousTag( final String previousVersion )
    {
        _previousTag = previousVersion;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setHost( final String host )
    {
        _host = host;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setPath( final String path )
    {
        _path = path;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setModules( final String modules )
    {
        if ( modules != null )
        {
            _modules = modules.split( ";" );
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setQueryParam( final String queryParam )
    {
        _queryParam = queryParam;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setCsvParam( final String csvParam )
    {
        _csvParam = csvParam;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setAdditionalParams( final String additionalParams )
    {
        _additionalParams = additionalParams;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setContentRoot( final String contentRoot )
    {
        _contentRoot = contentRoot;
    }

    public void setCurrentTag( final String currentTag )
    {
        _currentTag = currentTag;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setQuery( final String query )
    {
        _query = query;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setMessageIgnorePattern( final String messageIgnorePattern )
    {
        _messageIgnorePattern = messageIgnorePattern;
    }

    @SuppressWarnings("UnusedDeclaration")
    // used in the ant task
    public void setTimeoutInMinutes( final String timeoutInMinutes )
    {
        _timeoutInMinutes = Long.parseLong( timeoutInMinutes );
    }

    public class Change implements Comparable
    {
        private final String _author;

        private final String _module;

        private final String _comment;

        private final String _type;

        public Change( final String author, final String module, final String comment, final String type )
        {
            _author = author;
            _module = module;
            _comment = comment;
            _type = type.toUpperCase();
        }

        @Override
        public int compareTo( final Object o )
        {
            if ( !( o instanceof Change ) )
            {
                return -1;
            }
            else
            {
                final Change other = ( Change ) o;
                if ( _module.compareTo( other._module ) == 0 )
                {
                    if ( _type.compareTo( other._type ) == 0 )
                    {
                        if ( _author.compareTo( other._author ) == 0 )
                        {
                            return _comment.compareTo( other._comment );
                        }
                        else
                        {
                            return _author.compareTo( other._author );
                        }
                    }
                    else
                    {
                        return _type.compareTo( other._type );
                    }
                }
                else
                {
                    return _module.compareTo( other._module );
                }
            }
        }

        public String getComment()
        {
            return _comment;
        }

        public String getAuthor()
        {
            return _author;
        }

        public String getModule()
        {
            return _module;
        }

        public String getType()
        {
            return _type;
        }
    }

    public class ChangedFile implements Comparable
    {
        private final String _author;

        private final String _module;

        private final String _revision;

        private final String _fileName;

        private final String _path;

        public ChangedFile( final String author, final String module, final String revision, final String path, final String fileName )
        {
            _author = author;
            _module = module;
            _revision = revision;
            _path = path;
            _fileName = fileName;
        }

        public String getModule()
        {
            return _module;
        }

        public String getRevision()
        {
            return _revision;
        }

        public String getFileName( final boolean includePackage )
        {
            if ( includePackage )
            {
                final int srcPos = _path.indexOf( "src" );
                final int modPos = _path.indexOf( _module );
                final int subStrPos = srcPos != -1 ? srcPos - 1 : ( modPos != -1 ? modPos + _module.length() : 0 );
                return ( _path.substring( subStrPos ) + '/' + _fileName );
            }
            else
            {
                return _fileName;
            }
        }

        public String getAuthor()
        {
            return _author;
        }

        public String getBrowseURL()
        {
            return "http://" + _host + "/browse/" + _contentRoot + '/' + _path + '/' + _fileName;
        }

        @Override
        public int compareTo( final Object o )
        {
            if ( !( o instanceof ChangedFile ) )
            {
                return -1;
            }
            else
            {
                final ChangedFile other = ( ChangedFile ) o;
                if ( _module.compareTo( other._module ) == 0 )
                {
                    if ( _path.compareTo( other._path ) == 0 )
                    {
                        return _fileName.compareTo( other._fileName );
                    }
                    else
                    {
                        return _path.compareTo( other._path );
                    }
                }
                else
                {
                    return _module.compareTo( other._module );
                }
            }
        }
    }

    private class ChangeLogFetcherTask implements Callable<ChangeSet>
    {
        private final String _url;

        private final String _module;

        private final Pattern _pattern;

        public ChangeLogFetcherTask( final String url, final String module, final Pattern pattern )
        {
            _url = url;
            _module = module;
            _pattern = pattern;
        }

        @Override
        public ChangeSet call() throws Exception
        {
            final ChangeSet changeSet = new ChangeSet();
            final long start = System.currentTimeMillis();
            long bytesRead = 0;
            try
            {
                final URL fishBowl = new URL( _url );
                final StringBuilder out = new StringBuilder();

                try (Reader in = new InputStreamReader( fishBowl.openStream(), "UTF-8" ))
                {
                    int read;
                    final char[] buffer = new char[0x10000];
                    do
                    {
                        read = in.read( buffer, 0, buffer.length );
                        if ( read > 0 )
                        {
                            bytesRead += read;
                            out.append( buffer, 0, read );
                        }
                    }
                    while ( read >= 0 );
                }

                final String resultString = out.toString();
                if ( !resultString.isEmpty() )
                {
                    final String[] lines = resultString.split( TERM_SEQ );
                    final String mod = _module.substring( _module.lastIndexOf( '/' ) + 1 );
                    boolean firstLine = true;
                    for ( final String line : lines )
                    {
                        if ( !firstLine )
                        {
                            final String fileName = extractFileName( line );
                            final String path = extractPath( line );
                            final String version = extractVersion( line );
                            final String author = extractAuthor( line );
                            final String comment = extractComment( line );
                            if ( _pattern != null && comment != null && !_pattern.matcher( comment.trim() ).matches() )
                            {
                                final String[] commentLines = comment.trim().split( "\n" );
                                Arrays.sort( commentLines );
                                for ( final String cLine : commentLines )
                                {
                                    final Matcher m = COMMIT_TYPE.matcher( cLine );
                                    final String type;
                                    if ( m.matches() )
                                    {
                                        type = m.group( 1 );
                                    }
                                    else
                                    {
                                        type = "MISC";
                                    }
                                    changeSet.addChange( new Change( author, mod, cLine, type ) );
                                }
                                changeSet.addChangedFile( new ChangedFile( author, mod, version, path, fileName ) );
                            }
                        }
                        else
                        {
                            firstLine = false;
                        }
                    }
                }
            }
            catch ( final MalformedURLException e )
            {
                log( "MalformedURLException: Failed to fetch content from " + _url, Project.MSG_ERR );
            }
            catch ( final IOException e )
            {
                log( "IOException: Failed to fetch content from " + _url, e, Project.MSG_ERR );
            }
            finally
            {
                final double duration = ( double ) Math.round( ( System.currentTimeMillis() - start ) / 1000.0 * 100 ) / 100.0;
                log( "Finished fetching the changelog (" + duration + " sec, " + bytesRead + " bytes read)", Project.MSG_INFO );
            }
            return changeSet;
        }
    }

    private class ChangeSet
    {
        private final TreeSet<Change> _localChanges = new TreeSet<>();

        private final TreeSet<ChangedFile> _localChangedFiles = new TreeSet<>();

        public void addChangedFile( final ChangedFile changedFile )
        {
            _localChangedFiles.add( changedFile );
        }

        public void addChange( final Change change )
        {
            _localChanges.add( change );
        }

        public Collection<ChangedFile> getChangedFiles()
        {
            return _localChangedFiles;
        }

        public TreeSet<Change> getChanges()
        {
            return _localChanges;
        }
    }

    private class FutureWrapper
    {
        private final Future<ChangeSet> _future;

        private final String _url;

        public FutureWrapper( final Future<ChangeSet> future, final String url )
        {
            _future = future;
            _url = url;
        }

        public Future<ChangeSet> getFuture()
        {
            return _future;
        }

        public String getUrl()
        {
            return _url;
        }
    }
}
