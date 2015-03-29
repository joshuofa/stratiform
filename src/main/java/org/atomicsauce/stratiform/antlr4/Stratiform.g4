grammar Stratiform;

// lexer rules

COMMENT : '#' ~[\r\n]* -> skip ;

WHITESPACE : [ \t\r\n\u000C]+ -> skip ;

START_TEMPLATE : 'stratiform_template:' ;

TEMPLATE_VERSION : 'template_version' ;

TEMPLATE_DESCRIPTION : 'template_description' ;

START_PARAMETERS : 'parameters:' ;

PARAMETER_TYPE : ( 'string' | 'cidr' );

START_RESOURCES : 'resources:' ;

VPC_RESOURCE_TYPE : 'vpc' ;

ROUTE_TABLE_RESOURCE_TYPE : 'rtbl' ;

NETWORK_ACL_RESOURCE_TYPE : 'nacl' ;

SUBNET_RESOURCE_TYPE : 'subnet' ;

RESOURCE_NAME : 'name' ;

RESOURCE_DESCRIPTION : 'description' ;

START_PROPERTIES : 'properties:' ;

CIDR_BLOCK_PROPERTY_REFERENCE : 'cidr_block' ;

VPC_ID_PROPERTY_REFERENCE : 'vpc_id' ;

ROUTE_TABLE_ID_PROPERTY_REFERENCE : 'route_table_id' ;

NETWORK_ACL_ID_PROPERTY_REFERENCE : 'network_acl_id' ;

AVAILABILITY_ZONE_PROPERTY_REFERENCE : 'availability_zone' ;

HAS_VALUE : '|' ;

REFERENCED_AS : '::' ;

REFERENCE : [a-z] [a-z\_]+ [a-z] ;

STRING : '"' ( '\\"' | . )*? '"' ;

// parser rules

stackTemplate : START_TEMPLATE 
                stackPreamble
                stackParameters
                stackResources
                ;

stackPreamble : templateVersion
                templateDescription
                ;
                
templateVersion : TEMPLATE_VERSION HAS_VALUE STRING ;

templateDescription : TEMPLATE_DESCRIPTION HAS_VALUE STRING ;

stackParameters : START_PARAMETERS parameterDefinition+ ;

parameterDefinition : PARAMETER_TYPE REFERENCED_AS REFERENCE HAS_VALUE STRING ;

stackResources : START_RESOURCES resourceDefinition+ ;

resourceDefinition : ( vpcResourceDefinition
					 | routeTableResourceDefinition
					 | networkAclResourceDefinition
					 | subnetResourceDefinition
					 ) ;

vpcResourceDefinition : vpcResourceDeclaration
						resourceName
						resourceDescription
						vpcResourceProperties
						;

vpcResourceDeclaration : VPC_RESOURCE_TYPE REFERENCED_AS REFERENCE ;

vpcResourceProperties : START_PROPERTIES
						cidrBlockProperty ;

routeTableResourceDefinition : routeTableResourceDeclaration
				  			   resourceName
							   resourceDescription
							   routeTableResourceProperties
							   ;

routeTableResourceDeclaration : ROUTE_TABLE_RESOURCE_TYPE REFERENCED_AS REFERENCE ;

routeTableResourceProperties : START_PROPERTIES
							   vpcIdProperty ;

networkAclResourceDefinition : networkAclResourceDeclaration
				  			   resourceName
							   resourceDescription
							   networkAclResourceProperties
							   ;

networkAclResourceDeclaration : NETWORK_ACL_RESOURCE_TYPE REFERENCED_AS REFERENCE ;

networkAclResourceProperties : START_PROPERTIES
							   vpcIdProperty ;

subnetResourceDefinition : subnetResourceDeclaration
				  		   resourceName
						   resourceDescription
						   subnetResourceProperties
						   ;

subnetResourceDeclaration : SUBNET_RESOURCE_TYPE REFERENCED_AS REFERENCE ;

subnetResourceProperties : START_PROPERTIES
						   vpcIdProperty
						   routeTableIdProperty
						   networkAclIdProperty
						   availabilityZoneProperty
						   cidrBlockProperty
						   ;

resourceName : RESOURCE_NAME HAS_VALUE STRING ;

resourceDescription : RESOURCE_DESCRIPTION HAS_VALUE STRING ;

cidrBlockProperty : CIDR_BLOCK_PROPERTY_REFERENCE HAS_VALUE STRING ;

vpcIdProperty : VPC_ID_PROPERTY_REFERENCE HAS_VALUE STRING ;

routeTableIdProperty : ROUTE_TABLE_ID_PROPERTY_REFERENCE HAS_VALUE STRING ;

networkAclIdProperty : NETWORK_ACL_ID_PROPERTY_REFERENCE HAS_VALUE STRING ;

availabilityZoneProperty : AVAILABILITY_ZONE_PROPERTY_REFERENCE HAS_VALUE STRING ;
