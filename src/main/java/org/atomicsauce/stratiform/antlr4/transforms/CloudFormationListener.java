package org.atomicsauce.stratiform.antlr4.transforms;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.atomicsauce.stratiform.antlr4.StratiformBaseListener;
import org.atomicsauce.stratiform.antlr4.StratiformListener;
import org.atomicsauce.stratiform.antlr4.StratiformParser.NetworkAclResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.ParameterDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.ResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.RouteTableResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.StackParametersContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.StackResourcesContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.SubnetResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.TemplateDescriptionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.TemplateVersionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.VpcResourceDefinitionContext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

public final class CloudFormationListener extends StratiformBaseListener
		implements StratiformListener {

	private Map<String,Object> _map = null;

	private Pattern _single_inline_rx = Pattern.compile( "^\\{\\{ (?<inline>.*?) \\}\\}$" );
	private Pattern _inline_rx = Pattern.compile( "(?<pre>.*?)\\{\\{ (?<inline>.*?) \\}\\}(?<post>.*?)" );
	
	private Object _jsonStringToObject( String jstr ) {
		JsonReader jsr = new JsonReader( new StringReader( jstr ) );
		Gson gson = new GsonBuilder().create();
		return gson.fromJson( jsr, Map.class );
	}
	
	private String _camelCaseSfReference( String sfr ) {
		return StringUtils.remove( WordUtils.capitalize( sfr, '_'  ), '_' );
	}
	
	private Object _translateSfString( String sfs ) {

		// remove leading and trailing quotes
		String s = StringUtils.substring( sfs, 1, -1 );
		
		// quick return for special case of string consisting of only a single inlined reference
		Matcher sim = _single_inline_rx.matcher( s );
		if ( sim.matches() ) {
			return _jsonStringToObject( "{ \"Ref\": \""
									  + _camelCaseSfReference( sim.group( "inline" ) )
									  + "\" }" );
		}

		// check for embedded inline references, react accordingly
		Matcher m = _inline_rx.matcher( s );
		if ( m.find() ) {
			// string with inlines, convert to Fn::Join syntax and return as map
			StringBuffer sb = new StringBuffer();
			sb.append( "{ \"Fn::Join\": [ \"\", [ " );
			String pre, inline, post, tail;
			StringBuffer replace = new StringBuffer();
			m.reset();
			while ( m.find() ) {
				pre = m.group( "pre" );
				inline = _camelCaseSfReference( m.group( "inline" ) );
				post = m.group( "post" );
				if ( ! pre.isEmpty() ) {
					replace.append( "\"" + pre + "\", " );
				}
				replace.append( "{ \"Ref\": \"" + inline + "\" }" );
				if ( ! post.isEmpty() ) {
					replace.append( "\"" + post + "\"" );
				}
				m.appendReplacement( sb, replace.toString()  );
			}
			tail = m.appendTail( new StringBuffer() ).toString();
			if ( ! tail.isEmpty() ) {
				sb.append( ", \"" + tail + "\"" );
			}
			sb.append( " ] ] }" );
			return _jsonStringToObject( sb.toString() );
		} 
		else {
			// no inlines, return as a simple string
			return s;
		}
	}
	
	public CloudFormationListener() {
		_map = new LinkedHashMap<String,Object>();
	}
	
	public String prettyPrint() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson( _map );
	}

	@Override
	public void enterTemplateDescription( TemplateDescriptionContext ctx ) {
		_map.put( "Description", _translateSfString( ctx.STRING().getText() ) );
	}

	@Override
	public void enterTemplateVersion( TemplateVersionContext ctx ) {
		_map.put( "AWSTemplateFormatVersion", _translateSfString( ctx.STRING().getText() ) );
	}

	@Override
	public void enterStackParameters( StackParametersContext ctx ) {
		_map.put( "Parameters", new LinkedHashMap<String,Object>() );
	}
	
	@Override
	public void enterParameterDefinition( ParameterDefinitionContext ctx ) {
		String p_lid = _camelCaseSfReference( ctx.REFERENCE().getText() );
		Object p_default = _translateSfString( ctx.STRING().getText() );
		String sf_p_type = ctx.PARAMETER_TYPE().getText();
		String p_type = null;
		String p_allowed_pattern = null;
		String p_constraint_description = null;
		switch ( sf_p_type ) {
		case "string":
			p_type = "String";
			break;
		case "number":
			p_type = "Number";
			break;
		case "cidr":
			p_type = "String";
			p_allowed_pattern = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/(\\d{1,2})";
			p_constraint_description = "Must be a valid CIDR range of the form x.x.x.x/x.";
			break;
		default:
			throw new RuntimeException( "unrecognized Stratiform parameter type \"" + sf_p_type + "\"" );
		}

		Map<String,Object> this_p = new LinkedHashMap<String,Object>();
		this_p.put( "Type", p_type );
		this_p.put( "Default",  p_default );
		if ( p_allowed_pattern != null ) {
			this_p.put( "AllowedPattern", p_allowed_pattern );
		}
		if ( p_constraint_description != null ) {
			this_p.put( "ConstraintDescription", p_constraint_description );
		}
		
		@SuppressWarnings("unchecked")
		Map<String,Object> p_map = ( (Map<String,Object>) _map.get( "Parameters" ) );
		p_map.put( p_lid, this_p );
	}

	@Override
	public void enterStackResources( StackResourcesContext ctx ) {
		_map.put( "Resources", new LinkedHashMap<String,Object>() );
	}

	@Override
	public void enterVpcResourceDefinition( VpcResourceDefinitionContext ctx ) {
		String r_lid = _camelCaseSfReference( ctx.vpcResourceDeclaration().REFERENCE().getText() );
		Object r_name = _translateSfString( ctx.resourceName().STRING().getText() );
		Object r_description = _translateSfString( ctx.resourceDescription().STRING().getText() );
		String r_type = "AWS::EC2::VPC";

		Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
		r_properties.put( 
			"CidrBlock", 
			_translateSfString( ctx.vpcResourceProperties().cidrBlockProperty().STRING().getText() )
		);

		Map<String,Object> r_tags = new LinkedHashMap<String,Object>();
		r_tags.put( "Name", r_name );
		r_tags.put( "Description", r_description );
		r_properties.put( "Tags", r_tags );
		
		Map<String,Object> this_r = new LinkedHashMap<String,Object>();
		this_r.put( "Type",  r_type );
		this_r.put( "Properties", r_properties );
		
		@SuppressWarnings("unchecked")
		Map<String,Object> r_map = ( (Map<String,Object>) _map.get( "Resources" ) );
		r_map.put( r_lid, this_r );
	}

	@Override
	public void enterRouteTableResourceDefinition( RouteTableResourceDefinitionContext ctx) {
		String r_lid = _camelCaseSfReference( ctx.routeTableResourceDeclaration().REFERENCE().getText() );
		Object r_name = _translateSfString( ctx.resourceName().STRING().getText() );
		Object r_description = _translateSfString( ctx.resourceDescription().STRING().getText() );
		String r_type = "AWS::EC2::RouteTable";

		Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
		r_properties.put( 
			"VpcId", 
			_translateSfString( ctx.routeTableResourceProperties().vpcIdProperty().STRING().getText() )
		);

		Map<String,Object> r_tags = new LinkedHashMap<String,Object>();
		r_tags.put( "Name", r_name );
		r_tags.put( "Description", r_description );
		r_properties.put( "Tags", r_tags );
		
		Map<String,Object> this_r = new LinkedHashMap<String,Object>();
		this_r.put( "Type",  r_type );
		this_r.put( "Properties", r_properties );
		
		@SuppressWarnings("unchecked")
		Map<String,Object> r_map = ( (Map<String,Object>) _map.get( "Resources" ) );
		r_map.put( r_lid, this_r );
	}	

	@Override
	public void enterNetworkAclResourceDefinition( NetworkAclResourceDefinitionContext ctx ) {
		String r_lid = _camelCaseSfReference( ctx.networkAclResourceDeclaration().REFERENCE().getText() );
		Object r_name = _translateSfString( ctx.resourceName().STRING().getText() );
		Object r_description = _translateSfString( ctx.resourceDescription().STRING().getText() );
		String r_type = "AWS::EC2::RouteTable";

		Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
		r_properties.put( 
			"VpcId", 
			_translateSfString( ctx.networkAclResourceProperties().vpcIdProperty().STRING().getText() )
		);

		Map<String,Object> r_tags = new LinkedHashMap<String,Object>();
		r_tags.put( "Name", r_name );
		r_tags.put( "Description", r_description );
		r_properties.put( "Tags", r_tags );
		
		Map<String,Object> this_r = new LinkedHashMap<String,Object>();
		this_r.put( "Type",  r_type );
		this_r.put( "Properties", r_properties );
		
		@SuppressWarnings("unchecked")
		Map<String,Object> r_map = ( (Map<String,Object>) _map.get( "Resources" ) );
		r_map.put( r_lid, this_r );
	}

	@Override
	public void enterSubnetResourceDefinition( SubnetResourceDefinitionContext ctx ) {
		String r_lid = _camelCaseSfReference( ctx.subnetResourceDeclaration().REFERENCE().getText() );
		Object r_name = _translateSfString( ctx.resourceName().STRING().getText() );
		Object r_description = _translateSfString( ctx.resourceDescription().STRING().getText() );
		String r_type = "AWS::EC2::RouteTable";

		Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
		r_properties.put( 
			"VpcId", 
			_translateSfString( ctx.subnetResourceProperties().vpcIdProperty().STRING().getText() )
		);
		r_properties.put( 
			"RouteTableId", 
			_translateSfString( ctx.subnetResourceProperties().routeTableIdProperty().STRING().getText() )
		);
		r_properties.put( 
			"NetworkAclId", 
			_translateSfString( ctx.subnetResourceProperties().networkAclIdProperty().STRING().getText() )
		);
		r_properties.put( 
			"AvailabilityZone", 
			_translateSfString( ctx.subnetResourceProperties().availabilityZoneProperty().STRING().getText() )
		);
		r_properties.put( 
			"CidrBlock", 
			_translateSfString( ctx.subnetResourceProperties().cidrBlockProperty().STRING().getText() )
		);

		Map<String,Object> r_tags = new LinkedHashMap<String,Object>();
		r_tags.put( "Name", r_name );
		r_tags.put( "Description", r_description );
		r_properties.put( "Tags", r_tags );
		
		Map<String,Object> this_r = new LinkedHashMap<String,Object>();
		this_r.put( "Type",  r_type );
		this_r.put( "Properties", r_properties );
		
		@SuppressWarnings("unchecked")
		Map<String,Object> r_map = ( (Map<String,Object>) _map.get( "Resources" ) );
		r_map.put( r_lid, this_r );
	}
	
}
