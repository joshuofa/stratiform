package org.atomicsauce.stratiform.antlr4.transforms;

import java.io.IOException;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.atomicsauce.stratiform.antlr4.StratiformLexer;
import org.atomicsauce.stratiform.antlr4.StratiformParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class CloudFormationTransformer {

	public static void main( String[] args ) {

		try {
			String sft = args[0];			
			StratiformLexer lexer = new StratiformLexer( new ANTLRFileStream( sft ) );
			CommonTokenStream tokens = new CommonTokenStream( lexer );
			StratiformParser parser = new StratiformParser( tokens );
			
			ParseTreeWalker walker = new ParseTreeWalker();
			ParserRuleContext tree = parser.stackTemplate();
			CloudFormationListener listener = new CloudFormationListener();
			walker.walk( listener, tree );
			
			System.out.print( listener.prettyPrint() );

		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
	}

}
