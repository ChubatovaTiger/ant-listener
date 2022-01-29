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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildMailLinkGenerator extends Task
{
    private static final String TC_LINK = "###TC_LINK###";

    private static final String BUILD_NUMBER = "###BUILD_NUMBER###";

    private static final String TC_NUMBER_PATTERN = "number=\"([^\"]*)";

    private static final String TC_WEBURL_PATTERN = "webUrl=\"([^\"]*)";

    private static final String TC_STATUS_PATTERN = "status=\"([^\"]*)";

    private static final String SUCCESS = "SUCCESS";

    private String _successMsg = "JUnit tests for Daily Build " + BUILD_NUMBER + " successfully completed. Detailed results can be viewed <a href='" + TC_LINK + "'>here</a>.";

    private String _failMsg = "JUnit tests for Daily Build " + BUILD_NUMBER + " have failed. Detailed results can be viewed <a href='" + TC_LINK + "'>here</a>.";

    private String _teamCityUrl = "http://dailybuild.rdcgn.aws.aspect.com";

    private String _teamCityStatusRequest = "/app/rest/buildTypes/id:bt7/builds/running:true";

    private String _teamCityUser = "autotest";

    private String _teamCityPassword = "autotest";

    private String _outputProperty;

    @Override
    public void execute() throws BuildException
    {
        final String requestURL = _teamCityUrl + _teamCityStatusRequest;

        try
        {
            final Pattern buildNumberPattern = Pattern.compile( TC_NUMBER_PATTERN );
            final Pattern webURLPattern = Pattern.compile( TC_WEBURL_PATTERN );
            final Pattern statusPattern = Pattern.compile( TC_STATUS_PATTERN );
            log( "Requesting information for status." );
            final URLConnection connection = new URL( requestURL ).openConnection();
            connection.setDoInput( true );
            connection.setRequestProperty( "Authorization", userNamePasswordBase64( _teamCityUser, _teamCityPassword ) );
            connection.connect();
            String msg = "No status found for the build.";
            try (final BufferedReader in = new BufferedReader( new InputStreamReader( connection.getInputStream() ) ) )
            {
                final String content = in.readLine();
                log( "Retrieved '" + content + "'", Project.MSG_INFO );
                // extract the ID and other information
                final Matcher buildNumberMatcher = buildNumberPattern.matcher( content );
                String buildNumber = "";
                if ( buildNumberMatcher.find() )
                {
                    buildNumber = buildNumberMatcher.group( 1 );
                }
                final Matcher webURLMatcher = webURLPattern.matcher( content );
                String link = "";
                if ( webURLMatcher.find() )
                {
                    link = webURLMatcher.group( 1 );
                }
                final Matcher statusMatcher = statusPattern.matcher( content );
                String buildStatus = SUCCESS;
                if ( statusMatcher.find() )
                {
                    buildStatus = statusMatcher.group( 1 );
                }
                msg = SUCCESS.equals( buildStatus ) ? _successMsg : _failMsg;
                msg = msg.replace( TC_LINK, link );
                msg = msg.replace( BUILD_NUMBER, buildNumber );
            }
            catch ( IOException e )
            {
                log( "Failed to retrieve build status", e, Project.MSG_WARN );
            }
            log( "Output '" + msg + "'", Project.MSG_INFO );
            if ( _outputProperty != null )
            {
                getProject().setNewProperty( _outputProperty, msg );
            }
        }
        catch ( Exception ex )
        {
            ex.printStackTrace();
        }
    }

    private static String userNamePasswordBase64( final String username, final String password )
    {
        final String s = username + ":" + password;

        final String encs = new sun.misc.BASE64Encoder().encode( s.getBytes() );
        return "Basic " + encs;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setSuccessMsg( final String successMsg )
    {
        _successMsg = successMsg;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setFailMsg( final String failMsg )
    {
        _failMsg = failMsg;
    }

    public void setTeamCityUrl( final String teamCityUrl )
    {
        _teamCityUrl = teamCityUrl;
    }

    public void setTeamCityStatusRequest( final String teamCityStatusRequest )
    {
        _teamCityStatusRequest = teamCityStatusRequest;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setTeamCityUser( final String teamCityUser )
    {
        _teamCityUser = teamCityUser;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setTeamCityPassword( final String teamCityPassword )
    {
        _teamCityPassword = teamCityPassword;
    }

    @SuppressWarnings( "UnusedDeclaration" )
    // used in the ant task
    public void setOutputProperty( final String outputProperty )
    {
        _outputProperty = outputProperty;
    }

    public static void main( final String[] args )
    {
        final BuildMailLinkGenerator generator = new BuildMailLinkGenerator();
        generator.setTeamCityUrl( "http://dailybuild.rdcgn.aws.aspect.com" );
        generator.setTeamCityStatusRequest( "/app/rest/buildTypes/id:bt2/builds/running:true" );
        generator.execute();
    }
}
