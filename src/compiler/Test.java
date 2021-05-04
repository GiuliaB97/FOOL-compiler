package compiler;

import java.io.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import compiler.lib.*;
import compiler.exc.*;
//import svm.*;
import visualsvm.*;//needed from the visual svm 
import java.nio.file.*;//needed from the visual svm 
/**
 * The aim of the project: takes a program written in FOOL language and manage to execute it.
 * The process to pass from a FOOL's program to an executable one involves several steps which involves 
 * two main entities the compiler and the SVM.
 * Their working flow can be summarized as follow:
 * 
 * ----here start the compiler's works
 * 1. The program of the file.fool is converted in a stream of chars;
 * 2. The aforementioned stream is taken from the lexer (class generated automatically by antlr) 
 * 		which transforms it in a stream of token;
 * 3. That latter stream is passed to the parser  (class generated automatically by antlr) 
 * 		which generate the ST;
 * 4. The ASTGenerationVisitor visits the ST, in order to create an abstract representation of the tree (AST),
 * 		which will be used by the other visitor to check the correctness of the program;
 * 5.a SymbolTableVisitor: takes the AST and uses it to match declarations with the corresponding usages.
 * 5.b TypeCheckVisitor: takes the AST and uses it to check if the the rules of the FOOL language are respected,
 * 						the result of its visit is used by the PrintEASTVisitor to print a representation of the program ;
 * ----end front-end phases: if you arrive there, then the tree is complete and you can start to produce the code;
 * 5.c CodeGenerationVisitor: produce an intermediate code representation (fool.asm);
 * 
 * ---- here start the work of SVM
 * 6. The intermediate representation is converted in a new stream of chars
 * 7. The chars stream is used from the lexer of the SVM to produce a stream of token;
 * 8. The token's stream is taken from the SVM parser which produces the assembly code,
 * 9. That will be executed by the ExecuteVM;
 */
public class Test {
    public static void main(String[] args) throws Exception {
   			
    	String fileName = "quicksort_ho.fool";

    	CharStream chars = CharStreams.fromFileName(fileName);
    	FOOLLexer lexer = new FOOLLexer(chars);
    	CommonTokenStream tokens = new CommonTokenStream(lexer);
    	FOOLParser parser = new FOOLParser(tokens);

    	System.out.println("Generating ST via lexer and parser.");
    	ParseTree st = parser.prog();
    	System.out.println("You had "+lexer.lexicalErrors+" lexical errors and "+
    		parser.getNumberOfSyntaxErrors()+" syntax errors.\n");

    	System.out.println("Generating AST.");
    	ASTGenerationSTVisitor visitor = new ASTGenerationSTVisitor(); // use true to visualize the ST
    	Node ast = visitor.visit(st);
    	System.out.println("");

    	System.out.println("Enriching AST via symbol table.");
    	SymbolTableASTVisitor symtableVisitor = new SymbolTableASTVisitor();
    	symtableVisitor.visit(ast);
    	System.out.println("You had "+symtableVisitor.stErrors+" symbol table errors.\n");

    	System.out.println("Visualizing Enriched AST.");
    	new PrintEASTVisitor().visit(ast);
    	System.out.println("");

    	System.out.println("Checking Types.");
    	try {
    		TypeCheckEASTVisitor typeCheckVisitor = new TypeCheckEASTVisitor();
    		TypeNode mainType = typeCheckVisitor.visit(ast);
    		System.out.print("Type of main program expression is: ");
    		new PrintEASTVisitor().visit(mainType);
    	} catch (IncomplException e) {    		
    		System.out.println("Could not determine main program expression type due to errors detected before type checking.");
    	} catch (TypeException e) {
    		System.out.println("Type checking error in main program expression: "+e.text); 
    	}       	
    	System.out.println("You had "+FOOLlib.typeErrors+" type checking errors.\n");

    	int frontEndErrors = lexer.lexicalErrors+parser.getNumberOfSyntaxErrors()+symtableVisitor.stErrors+FOOLlib.typeErrors;
		System.out.println("You had a total of "+frontEndErrors+" front-end errors.\n");
		
		if ( frontEndErrors > 0) System.exit(1);   

    	System.out.println("Generating code.");
    	String code = new CodeGenerationASTVisitor().visit(ast);        
    	BufferedWriter out = new BufferedWriter(new FileWriter(fileName+".asm")); 
    	out.write(code);
    	out.close(); 
    	System.out.println("");

    	System.out.println("Assembling generated code.");
    	CharStream charsASM = CharStreams.fromFileName(fileName+".asm");
    	SVMLexer lexerASM = new SVMLexer(charsASM);
    	CommonTokenStream tokensASM = new CommonTokenStream(lexerASM);
    	SVMParser parserASM = new SVMParser(tokensASM);

    	parserASM.assembly();

    	// needed only for debug
    	System.out.println("You had: "+lexerASM.lexicalErrors+" lexical errors and "+parserASM.getNumberOfSyntaxErrors()+" syntax errors.\n");
    	if (lexerASM.lexicalErrors+parserASM.getNumberOfSyntaxErrors()>0) System.exit(1);

    	System.out.println("Running generated code via Stack Virtual Machine.");
    	//ExecuteVM vm = new ExecuteVM(parserASM.code);
    	ExecuteVM vm = new ExecuteVM(parserASM.code,parserASM.sourceMap,Files.readAllLines(Paths.get(fileName+".asm")));
    	vm.cpu();

    }
}

