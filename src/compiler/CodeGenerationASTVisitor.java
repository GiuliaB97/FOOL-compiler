package compiler;

import compiler.AST.*;
import compiler.lib.*;
import svm.ExecuteVM;
import compiler.exc.*;
import static compiler.lib.FOOLlib.*;

import java.util.ArrayList;
import java.util.List;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

  CodeGenerationASTVisitor() {}
  CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

  private static List<List<String>> dispatchTables;	//ne ho una per ogni classe indrizzi di tutti i metodi anche ereditati --> etichette; Ricorda dispatch table nello heap;
  													
  //Dispatch pointer in AR dell'ambiente globale--> è reperibile all'offset della classe-->valore iniziale fp 
	
  	/**
	 * Method for the generation of a program with declarations 
	 */
  	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		dispatchTables = new ArrayList<>(); 	//inizializzazione dispatch table; la faccio qui perchè
		String declCode = null;
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",	
			declCode, // generate code for declarations (allocation)			
			visit(n.exp),
			"halt",
			getCode()
		);
	}
  	
	/**
	 * Method for the generation of a program without declarations 
	 */
	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}
	
	
	/** 
	 * Method for the management of the print instruction 
	 */
	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}
	
	/** 
	 * Method for the generation of a 'if-then-else' expression
	 */
	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),	// it retrieves the condition result (it is constrained to be a boolean)
			"push 1",		// it pushs 'true' to compare
			"beq "+l1,		// if the condition is 'true', then jump to l1: then->branch
			visit(n.el),	// otherwise it executes the else branch 
			"b "+l2,		//	NB b unconditional jump-> it goes directly to end
			l1+":",
			visit(n.th),
			l2+":"
		);
	}
	
	/**
	 * Method for the generation of an identifier.
	 */
	@Override
	public String visitNode(IdNode n) {
		/*HO : If type is not functional no modification id needed 
		 * on the contrary if it is a functional type the address of AR(offset ID) for the function declaration 
		 * and function address offset ID - 1)
		 */
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");	// ascend Control Links chain until AR of declaration
		if(n.entry.type instanceof ArrowTypeNode) { //HO: if id is functional 
			return nlJoin( 
				"lfp", getAR, // retrieve address of frame containing "id" declaration			NB indir (fp) ad AR dichiaraz. funzione (recuperato a offset ID)
				"stm", 		  // set $tm to popped value (salvo l'indirizzo della AR - devo usarlo 2 volte)
		        "ltm", 		  // load on the stack the address of AR where ID is declared
				"push "+n.entry.offset, "add", "lw", // load AR where the function is declared (it will be used as AL for the call)
				"ltm", 		  // load the address of AR where ID is declared
				"push "+(n.entry.offset-1), "add", "lw" // load the function address (=label) NB indir funzione (recuperato a offset ID - 1)
				);
		} else {
			return nlJoin(
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				              // by following the static chain (of Access Links)		NB indir (fp) ad AR dichiaraz. funzione (recuperato a offset ID)
				"push "+n.entry.offset, "add", // compute address of "id" declaration
				"lw" 		  // load value of "id" variable
			);
		}
	}
	
	/////////////////////////////////////////////////////////////////////////// EXPRESSIONS
	
	/** 
	 * Method for the generation of a '*' expression
	 */
	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	/** 
	 * Method for the generation of a '+' expression
	 */
	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"				
		);
	}
	
	/** 
	 * Method for the generation of a '/' expression
	 */
	@Override
	public String visitNode(DivNode n) {//LE
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"div"
		);	
	}
	
	/** 
	 * Method for the generation of a '-' expression
	 */
	@Override
	public String visitNode(MinusNode n) {//LE
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"minus"
		);	
	}
	
	/** 
	 * Method for the generation of a '>=' expression
	 */
	@Override
	public String visitNode(GreaterEqualNode n) {//LE
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.right),				//I have not a 'bgeq' instruction therefore I just visit the value in a reverse order
			visit(n.left),
			"bleq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	/** 
	 * Method for the generation of a '<=' expression
	 */
	@Override
	public String visitNode(LessEqualNode n) {//LE
		if (print) printNode(n);
	 	String l1 = freshLabel();		// first == second; first <= second
	 	String l2 = freshLabel();		//	second>= first
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"bleq "+l1,					//bleq: it jumps if: first <= second
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	/** 
	 * Method for the generation of a '==' expression
	 */
	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),		//it retrieves the left value
			visit(n.right),		//it retrieves the right value
			"beq "+l1,			//if they are equals, then it jumps to l1-> equals label 
			"push 0",			//otherwise it pushes 0(=false) and jump to the end
			"b "+l2,
			l1+":",
			"push 1",			//it pushes 1(=true)
			l2+":"
		);
	}

	/** 
	 * Method for the generation of a '!' expression
	 */
	@Override
	public String visitNode(NotNode n) {//Idea: 1 - n.exp where n.exp it is constrained to be a boolean
		if (print) printNode(n);
		return nlJoin(
			"push 1",
			visit(n.val),	 
			"sub"					
		);	
	}
	
	/** 
	 * Method for the generation of a '||' expression
	 */
	@Override
	public String visitNode(OrNode n) {//LE
		if (print) printNode(n);
		String l1 = freshLabel();		//result = 1
		String l2 = freshLabel();		//result = 0
		return nlJoin(
			visit(n.left),
			"push 1",			
			"beq " + l1,			//if first ==1; it returns 1 without checking the whole expression
			 visit(n.right),		//else if first ==0, then it checks the second value; if second==1 then it returns 1
			"push 1",
			"beq"+l1+":",
			"push 0",				//it pushed 0 and then it jumps directly to end
			"b"+ l2,				
			l1+":",
			"push 1",
			l2+":"	
		);	
	}
	
	/** 
	 * Method for the generation of a '&&' expression
	 */
	// Idea:considera l'aritmetica binaria 0*0=0; 0*1=0, ecc.
	@Override
	public String visitNode(AndNode n) {//LE
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"					
		);	
	}
	
	/** 
	 * Method for the generation of a integer node
	 */
	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}
	
	/** 
	 * Method for the generation of a boolean node
	 */
	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}
	
	/** 
	 * Method for the generation of a empty node
	 */
	@Override
	public String visitNode(EmptyNode n) {//OO
		if (print) printNode(n);
		return "push -1";//-1 è un valore diverso da qualsiasi object pointer di qualsiasi oggetto creato
	}

	/** 
	 * Method for the generation of a call of a function or a method inside a class
	 */
	@Override
	public String visitNode(CallNode n) {//OO
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));// it creates code for parameter expressions in reversed order
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");// it finds the AL (pointer to frame of function's declaration, reached as for variable id)
		if(n.entry.type instanceof MethodTypeNode) {//OO: chiamata di un metodo locale cioè dall'interno di un altro metodo di un oggetto
			return nlJoin(
				"lfp", 			// push CL (pointer to caller's frame) used to ascend to declaration AR
				argCode, 		// generate code for argument expressions in reversed order
				"lfp", getAR,   // raggiungo l'address del frame contente la dichiarazione di ID, in pratica arrivo all'oggetto sull'heap
	                            // seguendo sempre la catena statica di Access Links (come la normale chiamata di una funzione)
	            "stm","ltm","ltm",// duplicate top of the stack (contains AR of declaration)
	            "lw",		  	// load the address of the obj in the dispatch table(why? l'obj pointer points to the address of the dispatch pointer)
	            "push "+n.entry.offset, "add", // calculate the address of the method (label) in the dispatch table
	            "lw", 			// get value (label(=address) of method's subroutine);
	            "js" 			// jump to popped address[ of the subroutine] (saving address of subsequent instruction in $ra)			);
	            );
		} else {
			return nlJoin(
				"lfp", 		  // load Control Link (pointer to frame of function "id" caller)
				argCode, 	  // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
	                          // by following the static chain (of Access Links)
	            "stm", 		  // set $tm to popped value (lo devo usare 2 volte)
	            "ltm", "push "+n.entry.offset, "add", "lw", 	//carico Access Link - andandolo a prendere nello stack in posizione precedente all'indirizzo della funzione
	            "ltm", "push "+(n.entry.offset-1), "add", "lw", //carico indirizzo funzione
	            "js"  		  // jump to popped address[ of the subroutine] (saving address of subsequent instruction in $ra)
			);
		}
	}
	
	
	/** 
	 * Method for the generation of an object instantiation
	 */
	@Override
	public String visitNode(NewNode n) {//OO
		if (print) printNode(n,n.id);
		String argCode = null, putArgsOnHeap = null;
		for (int i=0 ; i<n.arglist.size() ; i++) {
			argCode=nlJoin(argCode,visit(n.arglist.get(i))); // generate code for every field
			putArgsOnHeap = nlJoin(putArgsOnHeap, 
					"lhp", 		
					"sw",					//store the value on the stack on the heap			
					"lhp","push 1","add","shp"	// increment hp
					);
		}
		return nlJoin(
				argCode,
				putArgsOnHeap,
				
				"push "+(ExecuteVM.MEMSIZE+n.entry.offset),// push dispatch pointer's addres
				"lw",		// put the dispatch pointer on top of the stack

				"lhp",		// push hp on stack
				"sw",		// store he dispatch pointer in hp
				"lhp",		// copy object pointer (to be returned) on the stack; put hp on stack
				
				"lhp","push 1","add","shp"//increment hp
				);
	}

	//////////////////////////////////////DECLARATIONS
	
	/**
	 * Method for the generation of a function definition
	 */
	@Override
	public String visitNode(FunNode n) {//HO: modified for functional type management 
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
			if( ((DecNode)dec).getType() instanceof ArrowTypeNode) {// functional declarations occupy double space 
				popDecl = nlJoin(popDecl,"pop");					// therefore an additional pop must be added
			}																		
		}
		for (int i=0;i<n.parlist.size();i++) {
			popParl = nlJoin(popParl,"pop");
			if(n.parlist.get(i).getType() instanceof ArrowTypeNode) { 	// functional declarations occupy double space 
				popParl = nlJoin(popParl,"pop");						// therefore an additional pop must be added
			}																		
		}
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":",
				"cfp", 			// set $fp to $sp value
				"lra", 			// load $ra value
				declCode, 		// generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), 	// generate code for function body expression
				"stm", 			// set $tm to popped value (function result)
				popDecl, 		// remove local declarations from stack
				"sra", 			// set $ra to popped value
				"pop", 			// remove Access Link from stack
				popParl, 		// remove parameters from stack
				"sfp", 			// set $fp to popped value (Control Link)
				"ltm", 			// load $tm value (function result)
				"lra", 			// load $ra value
				"js"  			// jump to to popped address
			)
		);
		return nlJoin("lfp", "push "+funl);		
	}

	/**
	 *  Method for the generation of a variable (evaluate expression)
	 */
	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}
	
	/** 
	 * Method for the generation of a method definition
	 */
	@Override
	public String visitNode(MethodNode n) {// NB parameters push is done by call/class-call node
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");	// NB i metodi non possono avere dichairazioni funzionali
			
		}
		for (int i=0;i<n.parlist.size();i++)  {
			popParl = nlJoin(popParl,"pop");
			if(n.parlist.get(i).getType() instanceof ArrowTypeNode) { 	//dichiarazioni funzionali hanno offset doppio 
				popParl = nlJoin(popParl,"pop");						//quindi devo aggiungere una pop
			}	
		}
		n.label = freshFunLabel();//genero un etichetta per il suo indirizzo e la metto nel campo label
		putCode(
			nlJoin(					//genero il codice del metodo e lo metto in putcode
				n.label+":",
				"cfp", 			// set $fp to $sp value
				"lra", 			// load $ra value
				declCode, 		// generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), 	// generate code for function body expression
				"stm", 			// set $tm to popped value (function result)
				popDecl, 		// remove local declarations from stack
				"sra", 			// set $ra to popped value
				"pop", 			// remove Access Link from stack
				popParl, 		// remove parameters from stack
				"sfp", 			// set $fp to popped value (=Control Link)  (caller AR)
				"ltm", 			// load $tm value (function result) on the top of the stack
				"lra", 			// load $ra value (return address)
				"js"  			// jump to to popped address
			)
		);
		return "";	//return null
	}
	
	/** 
	 * Method for the generation of a class definition
	 */
	@Override
	public String visitNode(ClassNode n) {
		if (print) printNode(n,n.id);
		if(n.superID!=null) {
			dispatchTables.add(new ArrayList<>(dispatchTables.get(-n.superEntry.offset-2))); // devo copiare tutta la dispatch table della classe da cui estendo ; 	la individuo in base a offset classe da cui eredito in "superEntry"per layout ambiente globale: posizione -offset-2 (a offset -1 credo ci sia l'indirizzo della funzione)	di dispatchTables
		} else {			
			dispatchTables.add(new ArrayList<>());
		}
		
		for(MethodNode m:n.methods) {
			visit(m);
			if(m.offset >= dispatchTables.get(dispatchTables.size()-1).size()) {
				dispatchTables.get(dispatchTables.size()-1).add(m.label);// normalmente si usa l'etichetta del metodo della super-classe
			} else {				
				dispatchTables.get(dispatchTables.size()-1).set(m.offset, m.label);// overriding: si usa l'etichetta del metodo della classe che estende
			}
		}
		String dispatchTablesOnHeap = null;// codice per generare sull'heap la dispach table
		for(String s: dispatchTables.get(dispatchTables.size()-1)) {//recupero la lista corretta
			dispatchTablesOnHeap = nlJoin(				// per ogni metodo aggiungo la label sull'heap
					dispatchTablesOnHeap,
					"push "+s,	// push the label
					"lhp", "sw",	// store label at address pointed by hp
					"lhp","push 1","add","shp"//increment hp		
					);
		}
		
		return nlJoin(
				"lhp",// metto sullo stack il dispatch pointer che punta alla dispach table della classe
				dispatchTablesOnHeap// poi il codice per generare la dispach table sull'heap 
				);		
	}
	/** 
	 * Method for the generation of a method call
	 */
	@Override
	public String visitNode(ClassCallNode n) {//OO:simile a CallNode solo che occorre risalire prima al refID
		if (print) printNode(n,n.methodID);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");	// it retrieves the correct AR of (object) declaration.
		return nlJoin(
			"lfp", 			// load Control Link (pointer to frame of function "id" caller)
			argCode, 		// generate code for argument expressions in reversed order
			"lfp", getAR, 	// retrieve address of frame containing "id" declaration
                          	// by following the static chain (of Access Links)
			
			"push "+n.entry.offset,
			"add",			// get object pointer's address
			"lw",			// load value of id variable(address of object instance)
			
			"stm","ltm", "ltm",// duplicate top of the stack (contains object pointer)
			"lw",			// put dispatch pointer on stack (follow the access link)
			"push "+n.methodEntry.offset,
			"add",			// get method's label address
			"lw",			//retrieve the address of the called method;  get value (label of method's subroutine)
			"js"			//jump to the method ( subroutine (put address of next instruction in ra))
		);
	}	
	
}