package org.atomicsauce.stratiform.antlr4.transforms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atomicsauce.stratiform.antlr4.StratiformBaseListener;
import org.atomicsauce.stratiform.antlr4.StratiformListener;
import org.atomicsauce.stratiform.antlr4.StratiformParser.NetworkAclResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.ParameterDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.RouteTableResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.StackParametersContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.StackResourcesContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.SubnetResourceDefinitionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.TemplateDescriptionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.TemplateVersionContext;
import org.atomicsauce.stratiform.antlr4.StratiformParser.VpcResourceDefinitionContext;
import org.atomicsauce.stratiform.exceptions.StratiformRuntimeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class CloudFormationListener extends StratiformBaseListener
        implements StratiformListener {
    private static final Logger LOG = LogManager.getLogger( CloudFormationListener.class.getName() );

    private static final String TEMPLATE_VERSION_KEY = "AWSTemplateFormatVersion";
    private static final String TEMPLATE_DESCRIPTION_KEY = "Description";
    private static final String TEMPLATE_PARAMETERS_KEY = "Parameters";
    private static final String TEMPLATE_RESOURCES_KEY = "Resources";
    private static final String RESOURCE_TYPE_KEY = "Type";
    private static final String RESOURCE_TYPE_VPC = "AWS::EC2::VPC";
    private static final String RESOURCE_TYPE_ROUTE_TABLE = "AWS::EC2::RouteTable";
    private static final String RESOURCE_TYPE_NETWORK_ACL = "AWS::EC2::NetworkAcl";
    private static final String RESOURCE_TYPE_SUBNET = "AWS::EC2::Subnet";

    private static final String RESOURCE_PROPERTIES_KEY = "Properties";
    private static final String RESOURCE_PROPERTIES_CIDR_BLOCK_KEY = "CidrBlock";
    private static final String RESOURCE_PROPERTIES_VPC_ID_KEY = "VpcId";
    private static final String RESOURCE_PROPERTIES_ROUTE_TABLE_ID_KEY = "RouteTableId";
    private static final String RESOURCE_PROPERTIES_NETWORK_ACL_ID_KEY = "NetworkAclId";
    private static final String RESOURCE_PROPERTIES_AVAILABILITY_ZONE_KEY = "AvailabilityZoneId";

    private static final String RESOURCE_PROPERTIES_TAGS_KEY = "Tags";
    private static final String RESOURCE_PROPERTIES_TAGS_NAME_KEY = "Name";
    private static final String RESOURCE_PROPERTIES_TAGS_DESCRIPTION_KEY = "Description";

    private static final String SF_STRING_TEXT = "[^\\{\\}]+";
    private static final String SF_STRING_INLINE = "\\{\\{ .+? \\}\\}";
    private static final Pattern SF_SINGLE_TEXT_STRING_RX = Pattern.compile( "^" + SF_STRING_TEXT + "$" );
    private static final Pattern SF_SINGLE_INLINE_STRING_RX = Pattern.compile( "^" + SF_STRING_INLINE + "$" );
    private static final Pattern SF_COMPLEX_STRING_RX = Pattern.compile(
            "(?<segm>" +
            SF_STRING_TEXT +
            "|" +
            SF_STRING_INLINE +
            ")" );

    private Map<String,Object> map = new LinkedHashMap<String,Object>();

    public CloudFormationListener() {
        super();
    }

    private String dequote( String sin ) {
        LOG.entry( sin );

        String sout = StringUtils.strip( sin, "\"" );

        return LOG.exit( sout );
    }

    private String debrace( String sin ) {
        LOG.entry( sin );

        String sout = StringUtils.removeEnd( StringUtils.removeStart( sin, "{{ " ), " }}" );

        return LOG.exit( sout );
    }

    private String removeUnderscoresAndCapitalize( String sin ) {
        LOG.entry( sin );

        String sout = StringUtils.remove( WordUtils.capitalize( sin, '_'  ), '_' );

        return LOG.exit( sout );
    }

    private Map<String,String> convertSfInlineToCfRef( String sf_inline ) {
        LOG.entry( sf_inline );

        Map<String,String> cf_ref = new LinkedHashMap<String,String>();
        cf_ref.put( "Ref", removeUnderscoresAndCapitalize( debrace( sf_inline ) ) );

        return LOG.exit( cf_ref );
    }

    private Map<String,Object> makeCfJoin( String delim, List<Object> loo ) {
        LOG.entry( delim, loo );

        List<Object> jparm = new ArrayList<Object>();
        jparm.add( delim );
        jparm.add( loo );

        Map<String,Object> j = new LinkedHashMap<String,Object>();
        j.put( "Fn::Join", jparm );

        return LOG.exit( j );
    }

    private Object translateSfStringToObject( String sfs ) {
        LOG.entry( sfs );

        // strip quotes off beginning and end of string
        String sfss = StringUtils.strip( sfs, "\"" );

        // three possible cases:
        if ( SF_SINGLE_TEXT_STRING_RX.matcher( sfss ).matches() ) {

            // plain text with no inlines, return as-is
            return LOG.exit( sfss );

        } else if ( SF_SINGLE_INLINE_STRING_RX.matcher( sfss ).matches() ) {

            // string composed of single inline, convert and return
            return LOG.exit( convertSfInlineToCfRef( sfss ) );

        } else {

            // mixed text and inlines, convert to join expression
            List<Object> loo = new ArrayList<Object>();
            String segm;
            Matcher m = SF_COMPLEX_STRING_RX.matcher( sfss );
            while( m.find() ) {
                segm = m.group( "segm" );
                if ( segm.startsWith( "{{ " ) && segm.endsWith( " }}" ) ) {
                    loo.add( convertSfInlineToCfRef( segm ) );
                } else {
                    loo.add( segm );
                }
            }
            Map<String,Object> cfj = makeCfJoin( "", loo );
            return LOG.exit( cfj );
        }
    }

    @Override
    public void enterTemplateVersion( TemplateVersionContext ctx ) {
        LOG.entry( ctx );

        map.put( TEMPLATE_VERSION_KEY, dequote( ctx.STRING().getText() ) );

        LOG.exit();
    }

    @Override
    public void enterTemplateDescription( TemplateDescriptionContext ctx ) {
        LOG.entry( ctx );

        map.put( TEMPLATE_DESCRIPTION_KEY, dequote( ctx.STRING().getText() ) );

        LOG.exit();
    }

    @Override
    public void enterStackParameters( StackParametersContext ctx ) {
        LOG.entry( ctx );

        map.put( TEMPLATE_PARAMETERS_KEY, new LinkedHashMap<String,Object>() );

        LOG.exit();
    }

    @Override
    public void enterParameterDefinition( ParameterDefinitionContext ctx ) {
        LOG.entry( ctx );

        String p_lid = removeUnderscoresAndCapitalize( ctx.REFERENCE().getText() );
        String p_default = dequote( ctx.STRING().getText() );
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
            throw new StratiformRuntimeException( "unrecognized Stratiform parameter type \"" + sf_p_type + "\"" );
        }

        Map<String,Object> p = new LinkedHashMap<String,Object>();
        p.put( "Type", p_type );
        p.put( "Default",  p_default );
        if ( p_allowed_pattern != null ) {
            p.put( "AllowedPattern", p_allowed_pattern );
        }
        if ( p_constraint_description != null ) {
            p.put( "ConstraintDescription", p_constraint_description );
        }

        @SuppressWarnings("unchecked")
        Map<String,Object> ps = (Map<String,Object>) map.get( TEMPLATE_PARAMETERS_KEY );
        ps.put( p_lid, p );

        LOG.exit();
    }

    @Override
    public void enterStackResources( StackResourcesContext ctx ) {
        LOG.entry( ctx );

        map.put( TEMPLATE_RESOURCES_KEY, new LinkedHashMap<String,Object>() );

        LOG.exit();
    }

    private void addResourceEntry( String r_lid,
                                   Object r_name,
                                   Object r_description,
                                   String r_type,
                                   Map<String,Object> r_properties ) {
        LOG.entry();

        Map<String,Object> r_tags = new LinkedHashMap<String,Object>();
        r_tags.put( RESOURCE_PROPERTIES_TAGS_NAME_KEY, r_name );
        r_tags.put( RESOURCE_PROPERTIES_TAGS_DESCRIPTION_KEY, r_description );
        r_properties.put( RESOURCE_PROPERTIES_TAGS_KEY, r_tags );

        Map<String,Object> r = new LinkedHashMap<String,Object>();
        r.put( RESOURCE_TYPE_KEY,  r_type );
        r.put( RESOURCE_PROPERTIES_KEY, r_properties );

        @SuppressWarnings("unchecked")
        Map<String,Object> rs = (Map<String,Object>) map.get( TEMPLATE_RESOURCES_KEY );
        rs.put( r_lid, r );

        LOG.exit();
    }

    @Override
    public void enterVpcResourceDefinition( VpcResourceDefinitionContext ctx ) {
        LOG.entry( ctx );

        String r_lid = removeUnderscoresAndCapitalize( ctx.vpcResourceDeclaration().REFERENCE().getText() );
        Object r_name = translateSfStringToObject( ctx.resourceName().STRING().getText() );
        Object r_description = translateSfStringToObject( ctx.resourceDescription().STRING().getText() );
        String r_type = RESOURCE_TYPE_VPC;

        Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
        r_properties.put(
            RESOURCE_PROPERTIES_CIDR_BLOCK_KEY,
            translateSfStringToObject( ctx.vpcResourceProperties().cidrBlockProperty().STRING().getText() )
        );

        addResourceEntry( r_lid, r_name, r_description, r_type, r_properties );

        LOG.exit();
    }

    @Override
    public void enterRouteTableResourceDefinition( RouteTableResourceDefinitionContext ctx) {
        LOG.entry( ctx );

        String r_lid = removeUnderscoresAndCapitalize( ctx.routeTableResourceDeclaration().REFERENCE().getText() );
        Object r_name = translateSfStringToObject( ctx.resourceName().STRING().getText() );
        Object r_description = translateSfStringToObject( ctx.resourceDescription().STRING().getText() );
        String r_type = RESOURCE_TYPE_ROUTE_TABLE;

        Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
        r_properties.put(
            RESOURCE_PROPERTIES_VPC_ID_KEY,
            translateSfStringToObject( ctx.routeTableResourceProperties().vpcIdProperty().STRING().getText() )
        );

        addResourceEntry( r_lid, r_name, r_description, r_type, r_properties );

        LOG.exit();
    }

    @Override
    public void enterNetworkAclResourceDefinition( NetworkAclResourceDefinitionContext ctx ) {
        LOG.entry( ctx );

        String r_lid = removeUnderscoresAndCapitalize( ctx.networkAclResourceDeclaration().REFERENCE().getText() );
        Object r_name = translateSfStringToObject( ctx.resourceName().STRING().getText() );
        Object r_description = translateSfStringToObject( ctx.resourceDescription().STRING().getText() );
        String r_type = RESOURCE_TYPE_NETWORK_ACL;

        Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
        r_properties.put(
            RESOURCE_PROPERTIES_VPC_ID_KEY,
            translateSfStringToObject( ctx.networkAclResourceProperties().vpcIdProperty().STRING().getText() )
        );

        addResourceEntry( r_lid, r_name, r_description, r_type, r_properties );

        LOG.exit();
    }

    @Override
    public void enterSubnetResourceDefinition( SubnetResourceDefinitionContext ctx ) {
        LOG.entry( ctx );

        String r_lid = removeUnderscoresAndCapitalize( ctx.subnetResourceDeclaration().REFERENCE().getText() );
        Object r_name = translateSfStringToObject( ctx.resourceName().STRING().getText() );
        Object r_description = translateSfStringToObject( ctx.resourceDescription().STRING().getText() );
        String r_type = RESOURCE_TYPE_SUBNET;

        Map<String,Object> r_properties = new LinkedHashMap<String,Object>();
        r_properties.put(
            RESOURCE_PROPERTIES_VPC_ID_KEY,
            translateSfStringToObject( ctx.subnetResourceProperties().vpcIdProperty().STRING().getText() )
        );
        r_properties.put(
            RESOURCE_PROPERTIES_ROUTE_TABLE_ID_KEY,
            translateSfStringToObject( ctx.subnetResourceProperties().routeTableIdProperty().STRING().getText() )
        );
        r_properties.put(
            RESOURCE_PROPERTIES_NETWORK_ACL_ID_KEY,
            translateSfStringToObject( ctx.subnetResourceProperties().networkAclIdProperty().STRING().getText() )
        );
        r_properties.put(
            RESOURCE_PROPERTIES_AVAILABILITY_ZONE_KEY,
            translateSfStringToObject( ctx.subnetResourceProperties().availabilityZoneProperty().STRING().getText() )
        );
        r_properties.put(
            RESOURCE_PROPERTIES_CIDR_BLOCK_KEY,
            translateSfStringToObject( ctx.subnetResourceProperties().cidrBlockProperty().STRING().getText() )
        );

        addResourceEntry( r_lid, r_name, r_description, r_type, r_properties );

        LOG.exit();
    }

    public String toPrettyPrintedJsonString() {
        LOG.entry();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String js = gson.toJson( map );

        return LOG.exit( js );
    }
}
