package compiler;

import java.util.*;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;
/**
 * The aim of the class is to enrich the AST; 
 * It is generated at compile time and it matches declarations with their occurrences, 
 * checking if they are multiple declared, not declared etc.
 */
public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	private List<Map<String, STentry>> symTable = new ArrayList<>();//It is used when the program meets  declarations and uses (and enter or exit in a new scope)
	private int nestingLevel=0; //Current nesting level: global env=0
	private int decOffset=-2; /*counter for offset of local declarations at current nesting level 
								it starts from -2 and it is decremented each times a new declaration is met 
								*/
	int stErrors=0;

	private Map<String, Map<String,STentry>> classTable = new HashMap<>();	//OO: it maps each class identifier to its virtual table, its aim is to maintain the declarations of the fields and methods of the class, once the visitor finishes the visit of the class
	
	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	/**
	 * Method that looks for the entry corresponding at the id that takes as argument.
	 * It starts its search at the current nesting level and go back until it finds the entry it is looking for, 
	 * or it reaches the global environment (level = 0).
	 * 
	 * @param variable id
	 * @return entry
	 */
	private STentry stLookup(String id) {
		int j= this.nestingLevel; 					
		STentry entry  =null;
		while (j>=0 && entry==null) {				
			entry=this.symTable.get(j--).get(id); 	
		}
		return entry;								
	}
	
	/**
	 * Method that handles the root of a 'let-in' program.
	 * It is responsible for the creation of the table for the global environment (it will contain the declarations for the nesting level =0).
	 * Next it visits all its declaration and its body expression, 
	 * finally it removes the table it had created because when all uses have been processed to link 
	 * each ID node in the abstract-syntax tree with the corresponding symbol-table entry,
	 * then the symbol table itself is no longer needed.
	 * NB that should be the last table present as each block is responsible for the deletion of its own table.
	 * 
	 */
	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry>hm= new HashMap<>();	
		symTable.add(hm); 							
		for (Node dec : n.declist) visit(dec);															
		visit(n.exp);								
		symTable.remove(0);							
		return null;		//the visit returns 'void' because the aim is to match declaration with use, there is no need to return something
	}
	
	/**
	 * Method responsible of the management of a program without declaration.
	 * If a program has no declaration, then it will not use the symbol table. 
	 */
	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	
	/**
	 * Method responsible for the management of the declaration of a function.
	 * Since, all declarations must be at nesting level 0, it does not have to create one,
	 * it gets it and creates a new entry for its declaration (its id) and try to insert it in the table.
	 * Next, it creates a table for its parameters (it is entering in the internal scope of the function, therefore a new table is needed) 
	 * inserting them in the new table.
	 * Finally it launches the visit on its declaration and body expression.
	 * 
	 * NB since the type of the function is not set in the constructor, it musts be set here.
	 */
	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		n.setType(new ArrowTypeNode(parTypes, n.retType));												//function type is not set in the constructor (AST)
		
		STentry entry = new STentry(nestingLevel, 
				new ArrowTypeNode(parTypes,n.retType),decOffset--);
		decOffset--;																					//it updates the offset, so the next operation will have the correct value
		
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {	//it checks if the entry is already present in the table; NB 'put' returns the old value associated to the key, if that latter is already present
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");		//n.id: function name
			stErrors++;
		} 
		
		//creare una nuova hashmap per la symTable
		nestingLevel++;																					//it is entering in a new scope so it needs to increment the nesting level
		Map<String, STentry> hmn = new HashMap<>();														//and create a new table
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; 																	// stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist) {																	//	it visits the function parameters 
			if(par.getType() instanceof ArrowTypeNode) parOffset++;										//	functional types occupy double space
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {			// Again checks for multiple declarations
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		}
		for (Node dec : n.declist) visit(dec);															//next it visits its declarations
		visit(n.exp);
		
		symTable.remove(nestingLevel--);																//when it finishes it throws away the table because it is no longer needed
		decOffset=prevNLDecOffset; 																		//and it restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	
	/**
	 * Method that handles a variable declaration.
	 * Either if it is a global variable or, a variable in a function (/method), the table for the scope in which it is declared 
	 * must already exist, so it gets it, creates a new entry for itself and try to insert is in the table.
	 */
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);//B
		Map<String, STentry> hm = symTable.get(nestingLevel);//C
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		if(n.getType() instanceof ArrowTypeNode) decOffset--;
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {//it checks for multiple declarations
			System.out.println("Var id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		return null;
	}
	
	@Override
	public Void visitNode(PrintNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	/**
	 * Method that handle an 'if-then-else' expression.
	 * It just executes a visit on the three nodes expressions.
	 */
	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}
	
	/**
	 * Method that handle an '=' expression.
	 * It just executes a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle an '*' expression.
	 * It just executes a visit on the two nodes expressions.
	 */	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle an '+' expression.
	 * It just executes a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handles the call of a function, or a method if called within a class.
	 * It looks for the entry for the node that should have been previously declared
	 * and if it so it uses the entry in the table to update its fields.
	 * Next, it launches a visit on its arguments.
	 */
	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {																	//if the function/method is not declared
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {				//link the declaration with the usage
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
	/**
	 * Method that handles the call of a identifier.
	 * It looks for the entry for the node that should have been previously declared
	 * and if it so it uses the entry in the table to update its fields.
	 */
	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {				//link the declaration with the usage
			n.entry = entry;
			n.nl = nestingLevel; 
		}
		return null;
	}
	
	@Override
	public Void visitNode(BoolNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}
	
	@Override
	public Void visitNode(EmptyNode n) {
		if (print) printNode(n, "null");
		return null;
	}
	/**
	 * Method that handle a '>=' expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(GreaterEqualNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle a '<=' expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(LessEqualNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle a '!' expression.
	 * It just launches a visit on the node expression.
	 */
	@Override
	public Void visitNode(NotNode n) {//LE
		if (print) printNode(n);
		visit(n.val);
		return null;
	}
	/**
	 * Method that handle a '/' expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(DivNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle a '-' expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(MinusNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	/**
	 * Method that handle an '||' expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(OrNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	/**
	 * Method that handle an "&&" expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(AndNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handles the declaration of a class.
	 * Firstly, it crates a new table (Virtual Table) which will contains the declarations of the class;
	 * if the class extends another class the table should contain also the entries of the methods and 
	 * the fields of the superclass (the ones not overridden).
	 * Furthermore, the method need a new variable to manage the optimization task, 
	 * that latter is used to check if methods and fields of the super class have been overridden in a wrong way; 
	 * that is to say it is considered not correct to override fields and methods multiple times inside the same class.
	 *  
	 *  
	 *  Finally, it visits all its declaration, remove its table from the symbol table and reset the offset.
	 */
	@Override
	public Void visitNode(ClassNode n) { //OO (O)
		if (print) printNode(n);
		Map<String, STentry> virtualTable;													// OO: symboltable for the current level, it contains the declarations of the fields and methods of the class  (also the ones of the superclass not overridden)
		
		Set<String> hs = new HashSet<>();													// OO O: set that checks for methods and fields overridden in a wrong way
		
		if(nestingLevel!=0) {																// definitions must be at nesting level = 0, if not -> error 
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" not declared in global environment");
			stErrors++;
		}
		
		Map<String, STentry> hm = symTable.get(nestingLevel);								//it retrieves the table of the level 0 (created by prog-let-in)
		
		STentry entry = null;								
		n.setType(new ClassTypeNode(new ArrayList<>(), new ArrayList<>()));					//initialize the type field with an empty classTypeNode
		
		if(n.superID==null) {																//if the class does not extend from some other class allFields and allMethod lists must be empty
			entry = new STentry(nestingLevel, new ClassTypeNode(new ArrayList<>(), new ArrayList<>()),	decOffset--);
		}else {																				//if the class extends the new entry cannot be an empty classTypeNode 
			n.setSuperEntry(hm.get(n.superID));												//however the type must be the one of the superclass, furthermore it must also copy its fields and the methods				
			ClassTypeNode subclass = (ClassTypeNode)n.superEntry.type;		
			((ClassTypeNode)n.getType()).allFields.forEach(f-> subclass.allFields.add(f));
			((ClassTypeNode)n.getType()).allMethods.forEach(m-> subclass.allMethods.add(m));
			entry = new STentry(nestingLevel, new ClassTypeNode(new ArrayList<>(subclass.allFields), new ArrayList<>(subclass.allMethods)),decOffset--);
		}

		if (hm.put(n.id, entry) != null) {													//it add the new entry in the table, if its name is already present -> error
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
																		
		// It must add the class id(mapped to a new VT) in the CT (A)
		if(n.superID == null) {											// If the class does not extends another class the new VT is empty
			virtualTable = new HashMap<>();
		} else {														// otherwise it must copy the VT of the superclass
			virtualTable = new HashMap<>(classTable.get(n.superID));	// (it uses the ID of the super classe to get its map	
		}
		classTable.put(n.id, virtualTable); 							//  (A)
		nestingLevel++;													// update the nesting level becuase it is about to enter inside the class declaration 
		symTable.add(virtualTable);										
		
		int prevNLDecOffset=decOffset; 									// stores counter for offset of declarations at previous nesting level 
		
		int fieldOffset;
		if(n.superID==null) {											//If the class does not extends
			decOffset=0;													//methods starts at offset 0
			fieldOffset=-1;													//fields starts at offset -1
		}else {															//otherwise
			decOffset=((ClassTypeNode)entry.type).allMethods.size();		//the new methods starts after the last method inherited from the superclass	
			fieldOffset=-((ClassTypeNode)entry.type).allFields.size()-1;	//fields starts after the last field of the inherited
		}
		//VT e obj ClassTYpeNode are updated each time a method or a field is met
		
		for (FieldNode field : n.fields) {		//I cannot use a lambda to handle the loop because fieldOffset is not final 					
			if(!hs.contains(field.id)) {																		
				hs.add(field.id);
			} else {							// [OOo]: if a field has already been added to the set it should consider it an error
				System.out.println("Field id " + field.id + " already declared within the class "+n.id);
				stErrors++;
			}
											
			if(virtualTable.containsKey(field.id)) {//Methods cannot be overridden with fields, or declared multiple times within the same class
				if(virtualTable.get(field.id).type instanceof MethodTypeNode) {
					System.out.println("Cannot override method id " + field.id + " with a field at line "+ n.getLine());
					stErrors++;
				} else {							//the old STentry is thrown away and a new one takes its place(offset must remain the same of the old STentry)
					field.offset = virtualTable.get(field.id).offset;
					virtualTable.put(field.id, new STentry(nestingLevel,field.getType(),field.offset));
					((ClassTypeNode)hm.get(n.id).type).	//per i campi aggiorno arrayFields settando la posizione a -offset-1 al tipo(nostro layout primo campo è -1)
									allFields.set(-field.offset-1, field.getType());
				}
			} else {
				((ClassTypeNode)hm.get(n.id).type).allFields.add(field.getType());
				field.offset = fieldOffset;
				virtualTable.put(field.id, new STentry(nestingLevel,field.getType(),fieldOffset--));
			}
		}
		
		for (int i=0; i<n.methods.size(); i++) { //same thing of the fields
			if(!hs.contains(n.methods.get(i).id)) {
				hs.add(n.methods.get(i).id);
			} else {
				System.out.println("Method id " + n.methods.get(i).id + " already declared in class "+n.id);
				stErrors++;
			}
			visit(n.methods.get(i));
			if(n.methods.get(i).offset < ((ClassTypeNode)hm.get(n.id).type).allMethods.size()) {//update allMethods setting the offset position to the functional type (1rst method= offset 0)
				((ClassTypeNode)hm.get(n.id).type).allMethods.set(n.methods.get(i).offset, (MethodTypeNode) n.methods.get(i).getType());
			} else {				
				((ClassTypeNode)hm.get(n.id).type).allMethods.add( (MethodTypeNode) n.methods.get(i).getType());
			}
		}          
														// when exting from the declaration 
		symTable.remove(nestingLevel--);				// the level of the current VT must be removed 
		decOffset = prevNLDecOffset;					// reset l'offset
		return null;
		
	}
	
	/**
	 * Method that handles the declaration of a method.
	 * It get the table for its level, checks if its is is already present in the table:
	 * - if it is present the method then the method is trying to override a method of the superclass, 
	 * so it must create a new entry that will take the place of the method overridden (it should have the same offset).
	 * - if it is not present then, this is a new method that must be mapped to a new entry.
	 * Next, it does the checks on its arguments and executes a visit on them.
	 */
	@Override
	public Void visitNode(MethodNode n) {
		if (print) printNode(n);
		Map<String, STentry> virtualTable = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		
		if(virtualTable.containsKey(n.id)) {																	
			if(!(virtualTable.get(n.id).type instanceof MethodTypeNode)) {//methods can be overridden only with other method
				System.out.println("Cannot override method id " + n.id + " with a field at line "+ n.getLine());
				stErrors++;
			} else {
				n.offset = virtualTable.get(n.id).offset;//the old STentry is thrown away and a new one takes its place (offset must remain the same of the old STentry)
				virtualTable.put(n.id, new STentry(nestingLevel, new MethodTypeNode(parTypes,n.retType), n.offset));
			}
		} else {
			n.offset = decOffset;
			virtualTable.put(n.id, new STentry(nestingLevel, new MethodTypeNode(parTypes,n.retType),decOffset++));
		}
		
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();//a new map must be created because it is entering in the internal scope of the method
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; //it stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);            
		symTable.remove(nestingLevel--);// map of the current level must be removed because it is exiting from the scope
		decOffset=prevNLDecOffset; // it restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	/**
	 * Method that handles a method call (called with ID.ID2()).
	 * Firstly it retrieves, the entry of the class from the symbol table and it uses it to retrieve from the class table 
	 * the virtual table of the class and from that it gets the method entry.
	 * If it finds it, then it uses the entry to set the node's field.
	 */
	@Override
	public Void visitNode(ClassCallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.classID);
		if (entry != null) { 
			n.entry = entry;
		} else {
			System.out.println("Class id " + n.classID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		STentry methodEntry = classTable.get(((RefTypeNode)entry.type).id).get(n.methodID);
		if (methodEntry != null) {
			n.methodEntry = methodEntry;
			n.nl = nestingLevel;
		} else {
			System.out.println("Method id " + n.methodID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
	
	/**
	 * Methods that handles an object instantiation.
	 * Firstly, it retrieves the id of the class from the classTable if 
	 * it is not present then the program is trying to use a class that has not been declared.
	 * Next, it get the entry of the class from the symbol table to set the nodes' fields.
	 * Finally, it visit all the arguments.
	 */
	@Override
	public Void visitNode(NewNode n) {
		if (print) printNode(n);
		if(classTable.containsKey(n.id)) {
			STentry entry = symTable.get(0).get(n.id); //and STentry took at level 0 of the ST because class are at the beginning of the let-section
			n.entry = entry;
			n.nl = nestingLevel;
		} else {							//if it is not in class table then it has not been declared
			System.out.println("Class id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
}