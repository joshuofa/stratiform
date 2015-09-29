# org.atomicsauce.stratiform
A simplified DSL for AWS CloudFormation template creation.



To build this thing, you can use gradle.

In order to first generate the grammar you can use this command

gradle generateGrammarSource

Then this will compile the rest of the project

gradle compileJava

See https://docs.gradle.org/current/userguide/antlr_plugin.html