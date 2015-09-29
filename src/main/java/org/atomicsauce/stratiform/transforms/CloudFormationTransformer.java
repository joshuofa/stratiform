package org.atomicsauce.stratiform.transforms;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.atomicsauce.stratiform.antlr4.StratiformLexer;
import org.atomicsauce.stratiform.antlr4.StratiformParser;

import java.io.IOException;

public final class CloudFormationTransformer {
    private static final Logger LOG = LogManager.getLogger( CloudFormationTransformer.class.getName() );
    private static final Marker CFOUT_MARKER = MarkerManager.getMarker( "CFOUT" );

    private CloudFormationTransformer() {
    }

    public static void main( String[] args ) {
        LOG.entry( (Object[]) args );

        try {
            String sft = args[0];
            StratiformLexer lexer = new StratiformLexer( new ANTLRFileStream( sft ) );
            CommonTokenStream tokens = new CommonTokenStream( lexer );
            StratiformParser parser = new StratiformParser( tokens );

            ParseTreeWalker walker = new ParseTreeWalker();
            ParserRuleContext tree = parser.stackTemplate();
            CloudFormationListener listener = new CloudFormationListener();
            walker.walk( listener, tree );

            LOG.info( CFOUT_MARKER, listener.toPrettyPrintedJsonString() );

        } catch ( IOException e ) {
            LOG.fatal( "caught IOException", e );
        }

        LOG.exit();
    }
}
