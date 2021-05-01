package compiler;

import java.util.*;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;
/**
 * The aim of the class is to enrich the tree; 
 * It is generated at compile time and it matches declarations with their occurrences, 
 * checking if they are multiple declared, not declared etc.
 * The class contain a list of tables organized, whom organization depends on the scope:
 * global environment is at nesting level 0, 
 * the map and the nesting level variable are always updated 
 * (incrementing the nesting level when you enter in a new scope and decremented it when you go out)
 * therefore they point to the level in which the program is currently.
 *  
 *  
 * @author giuliabrugnatti
 *
 */
public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	private List<Map<String, STentry>> symTable = new ArrayList<>();/*It is used when the program meet declarations and uses (and enter or exit in a new scope)
	*/
	private int nestingLevel=0; //Nesting level attuale: parte da zero ambiente globale
	private int decOffset=-2; /*counter for offset of local declarations at current nesting level 
								parte da -2 e lo decommento ogni volta che incontro una nuova dichiarazione (di classe) 
								*/
	int stErrors=0;

	private Map<String, Map<String,STentry>> classTable = new HashMap<>();		//OO
	private Set<String> hs;														//OO O: 

	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	/**
	 * Method that look for the entry corresponding at the id that takes as argument.
	 * It starts its search at the current nesting level and go back until it found the entry it is looking for, 
	 * or it reaches the global environment (level = 0)
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
	 * Method that handles the root of a let-in program.
	 * It is responsible for the creation of the table for the global environment
	 * (it will contain the declarations for the nesting level =0).
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
	
	/*
	 * G. visito le dichiarazioni di variabili che possono essere variabili 
	 * 		o funzioni											for (Node dec : n.declist) visit(dec);		// G
	 * H. visito il corpo della mia funzione che può usare cose locali o cose in nesting 
	 * 		level inferiori (finoa d arrivare a zero)			visit(n.exp);								// H
	 * I. prima di terminare devo uscire dallo scope			this.symTable.remove(nestingLevel--);		// I
		
	 */
	/**
	 * Method responsible for the management of the declaration of a function.
	 * Since, all declarations must be at nesting level 0, it does not have to create one,
	 * it get it and create a new entry for its declaration (its id) and try to insert it in the table.
	 * Next, it creates a table for its parameters inserting them in the new table.
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
		n.setType(new ArrowTypeNode(parTypes, n.retType));												//function type is not set in the constructur (AST)
		
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
		Map<String, STentry> hmn = new HashMap<>();														//And create a new table
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
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);																//when it finishes it throws away the table becuase it is no loger needed
		decOffset=prevNLDecOffset; 																		//and it restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	
	/**
	 * Method that handles a variable declaration.
	 * EIther if it is a global variable or, a variable in a function (/method), the table for the scope in which it is declared 
	 * must already exist, so it gets it, create a new entry for itself and try to insert is in the table
	 */
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);//B
		Map<String, STentry> hm = symTable.get(nestingLevel);//C
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		if(n.getType() instanceof ArrowTypeNode) decOffset--;
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {//it checks for multiple declaration
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
	 * Method that handle an if-then-else expression.
	 * It just launches a visit on the three nodes expressions.
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
	 * Method that handle an equal expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle an times expression.
	 * It just launches a visit on the two nodes expressions.
	 */	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	/**
	 * Method that handle an plus expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
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

	//////////////////////////////////////////////////////////////////////////////
	/**
	 * Method that handle a greater-equal expression.
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
	 * Method that handle a less-equal expression.
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
	 * Method that handle a not expression.
	 * It just launches a visit on the node expression.
	 */
	@Override
	public Void visitNode(NotNode n) {//LE
		if (print) printNode(n);
		visit(n.val);
		return null;
	}
	/**
	 * Method that handle a division expression.
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
	 * Method that handle a minus expression.
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
	 * Method that handle an or expression.
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
	 * Method that handle an and expression.
	 * It just launches a visit on the two nodes expressions.
	 */
	@Override
	public Void visitNode(AndNode n) {//LE
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	////////////////////////////////////////////////////////////////////////
	@Override
	public Void visitNode(ClassNode n) {//l'aggiornamento del classTypeNode ora lo faccio direttamente qui; 
										//NB decorazione nodi AST ID2 (stentry della classe id2 in campo superentry), 
										//id2 deve essere in CT e STentry presa direttamente da livello 0 della ST
		if (print) printNode(n);
		Map<String, STentry> virtualTable;//Uso: quando visito lo scope interno di una classe la ST del livello corrispondente 
										//deve includere anche le Stentry per metodi e campi ereditati su cui non è stato fatto overriding
																		
		this.hs = new HashSet<>();		// Versione OO con ottimizzazioni: oggetto creato vuoto all'entrata della classe
										// serve per controllare l'erronea ridefinizione di campi e metodi all'interno della stessa classe
		if(nestingLevel!=0) {//Per com'è fatto il nostro layout la dichiarazione della classe deve trovarsi all'inizio, se non è così devo lanciare un errore
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" not declared in global environment");
			stErrors++;
		}
		
		//visito la classe dichiarata
		Map<String, STentry> hm = symTable.get(nestingLevel);			//Devo aggiungere a nesting level=0, il nome della classe mappato a una nuova Stentry
		//inizializzazione
		STentry entry = null;								
		n.setType(new ClassTypeNode(new ArrayList<>(), new ArrayList<>()));
		
		if(n.superID==null) {//se non eredito devo il tipo è un nuovo oggetto con una lista in allFields e allMethod vuota
			entry = new STentry(nestingLevel, 												//nestingLevel==0				 
								new ClassTypeNode(new ArrayList<>(), new ArrayList<>()),	// l'oggetto di tipo  classNode deve essere contenuto nella STentry			
								decOffset--);												//lo stack cresce verso il basso a ogni dichiarazione devo decrementare
		}else {				//se eredito il tipo viene creato copiando il tipo della classe da cui si eredita (copio tutto il contenuto)				
			n.setSuperEntry(hm.get(n.superID));
			ClassTypeNode subclass = (ClassTypeNode)n.superEntry.type;		//se eredita, il tipo della classe viene creato copiando quello ereditato
			((ClassTypeNode)n.getType()).allFields.forEach(f-> subclass.allFields.add(f));
			((ClassTypeNode)n.getType()).allMethods.forEach(m-> subclass.allMethods.add(m));
			entry = new STentry(nestingLevel, new ClassTypeNode(new ArrayList<>(subclass.allFields), new ArrayList<>(subclass.allMethods)),decOffset--);
		}
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
																		
	// Nella CT aggiungo il nome della classe mappato ad una nuova vitual table (A)
		if(n.superID == null) {											// Se non eredito la VT è vuota
			virtualTable = new HashMap<>();
		} else {														// se eredito allora, devo copiare il contenuto (tutto) della VT da cui eredito;
			virtualTable = new HashMap<>(classTable.get(n.superID));	// (uso l'ID della super classe per farmi restituire la mappa corrispondente	
		}
		classTable.put(n.id, virtualTable); 							// (A) 
		nestingLevel++;													// All'entrata dentro la dichiarazione della classe,creo un nuovo livello per la ST 
		symTable.add(virtualTable);										// vien posto essere la nuova VT creata
		
		int prevNLDecOffset=decOffset; 									// stores counter for offset of declarations at previous nesting level 
		
		int fieldOffset;
		if(n.superID==null) {
			decOffset=0;
			fieldOffset=-1;
		}else {
			decOffset=((ClassTypeNode)entry.type).allMethods.size();
			fieldOffset=-((ClassTypeNode)entry.type).allFields.size()-1;
		}
		//VT e obj ClassTYpeNode sono aggiornati tutte le volte che si incontrano: campi e metodi
		// Versione OO con ottimizzazioni: non bisogna considerare più overriding la ridefinizione di campi e metodi all'interno della stessa classe
		for (FieldNode field : n.fields) {		//qui non posso usare una lambda perchè: fieldOffset non è final (e non posso metterlo tale) 					
			if(!hs.contains(field.id)) {		//mentre scorro le dichiarazioni dei campi; li aggiungo al hs (se non ci sono già)																	
				hs.add(field.id);
			} else {													// se un nome di campo è già presente [OOo] non lo devo considerare overriding ma errore
				System.out.println("Field id " + field.id + " already declared within the class "+n.id);
				stErrors++;
			}
			if(virtualTable.containsKey(field.id)) {
				if(virtualTable.get(field.id).type instanceof MethodTypeNode) {
					System.out.println("Cannot override method id " + field.id + " with a field at line "+ n.getLine());
					stErrors++;
				} else {												//sostituisco nuova STentry alla vecchia preservando l’offset che era nella vecchia STentry
					field.offset = virtualTable.get(field.id).offset;
					virtualTable.put(field.id, new STentry(nestingLevel,field.getType(),field.offset));
					((ClassTypeNode)hm.get(n.id).type).					//per i campi aggiorno arrayFields settando la posizione a -offset-1 al tipo(nostro layout primo campo è -1)
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
	
	@Override
	public Void visitNode(MethodNode n) {
		if (print) printNode(n);
		Map<String, STentry> virtualTable = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		
		if(virtualTable.containsKey(n.id)) {
			if(!(virtualTable.get(n.id).type instanceof MethodTypeNode)) {
				System.out.println("Cannot override method id " + n.id + " with a field at line "+ n.getLine());
				stErrors++;
			} else {
				//sostituisco nuova STentry alla vecchia preservando l’offset che era nella vecchia STentry
				n.offset = virtualTable.get(n.id).offset;
				//virtualTable.put(n.id, new STentry(nestingLevel, new MethodTypeNode(parTypes,n.retType), n.offset));
				virtualTable.put(n.id, new STentry(nestingLevel, n.getType(), n.offset));
			}
		} else {
			n.offset = decOffset;
			//virtualTable.put(n.id, new STentry(nestingLevel, new MethodTypeNode(parTypes,n.retType),decOffset++));
			virtualTable.put(n.id, new STentry(nestingLevel, n.getType(),decOffset++));
		}
		
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();//a new map must be created 
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);            
		symTable.remove(nestingLevel--);// map of the current level must be removed because I am exiting from the scope
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}

	@Override
	public Void visitNode(ClassCallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.classID);
		if (entry != null) {//searched in the same way of IdNode andCallNode
			n.entry = entry;
		} else {
			System.out.println("Class id " + n.classID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		STentry methodEntry = classTable.get(((RefTypeNode)entry.type).id).get(n.methodID);
		if (methodEntry != null) {// searched in VT ( reached using CT of the class of the type RefTypeNode)
			n.methodEntry = methodEntry;
			n.nl = nestingLevel;
		} else {
			System.out.println("Method id " + n.methodID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
	
	@Override
	public Void visitNode(NewNode n) {
		if (print) printNode(n);
		if(classTable.containsKey(n.id)) {//ID must be in CT 
			STentry entry = symTable.get(0).get(n.id);//and STentry took at level 0 of the ST
			n.entry = entry;
			n.nl = nestingLevel;
		} else {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
}