package compiler;

import java.util.*;
import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;
/*Ottimizzazioni
 * * Ridefinizione Erronea di Campi e Metodi
 * • Rende possibile rilevare la ridefinizione (erronea) di campi e metodi con 
 * 	stesso nome effettuata all'interno della stessa classe
 * 		– la trattavamo come fosse un overriding
 * • In symbol table visitor, mentre si scorrono le dichiarazioni di campi 
 * 	e metodi di una classe,
 * 		– usare un campo del visitor contenente un oggetto HashSet<String> 
 * 			creato vuoto all’entrata nella classe
 * 		– ad ogni dichiarazione di campo o metodo:
 * • controllare se il suo nome è già presente nella HashSet
 * 		– se lo è notificare l’errore, altrimenti aggiungerlo alla HashSet
 * • gestire la dichiarazione come in precedenza
 */
public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	private Map<String, Map<String,STentry>> classTable = new HashMap<>(); //OO extension: per ogni classe dichiarata devo memorizzare campi e metodi
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	private Set<String> optimizer;
	private int nestingLevel=0; // current nesting level
	private int decOffset=-2; // counter for offset of local declarations at current nesting level 
	int stErrors=0;	
	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null) 
			entry = symTable.get(j--).get(id);	
		return entry;
	}

	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = new HashMap<>();
		symTable.add(hm);
	    for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		symTable.remove(0);
		return null;
	}

	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes,n.retType),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		} 
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
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
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		if(n.getType() instanceof ArrowTypeNode) {//HO l'offset si decrementa di 2
			decOffset--;
		}
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
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

	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}
	
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
////////////////////////////////////////////////////LANGUAGE EXTENSION NODES
	public Void visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	public Void visitNode(LessEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	public Void visitNode(NotNode n) {
		if (print) printNode(n);
		visit(n.val);
		return null;
	}
	
	public Void visitNode(MinusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	public Void visitNode(OrNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	public Void visitNode(DivNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	public Void visitNode(AndNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	/////////////////OO EXTENSION
	
	public Void visitNode(ClassNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(0);				// nestingLevel è sempre 0 per le dichiarazioni delle classi

		ClassTypeNode type = null;
		if (n.superID != null) { 								// se la classe eredita, devo partire dalla STentry della super-classe
			if (hm.containsKey(n.superID)) {
				n.superEntry = hm.get(n.superID);									// collego la super-entry
				
				ClassTypeNode superType = ((ClassTypeNode)n.superEntry.type);		// in particolare il tipo della classe usa come base il tipo della super-classe
				type=new ClassTypeNode(new ArrayList<>(superType.fields),new ArrayList<>(superType.methods));
			} else {
				System.out.println("Class id " + n.superID + " at line "+ n.getLine() +" not declared");
				stErrors++;
			}
			this.optimizer = new HashSet<>();
			
			//visito la classe dichiarata
			Map<String, STentry> hm = symTable.get(nestingLevel);
			STentry entry = null;
			n.setType(new ClassTypeNode(new ArrayList<>(), new ArrayList<>()));
			if(n.superID != null) {
				
				n.setSuperEntry(hm.get(n.superID));
				//se eredita, il tipo della classe viene creato copiando quello ereditato
				ClassTypeNode clone = (ClassTypeNode)n.superEntry.type;
				((ClassTypeNode)n.getType()).fields.addAll(0, clone.fields);
				((ClassTypeNode)n.getType()).methods.addAll(0, clone.methods);
				entry = new STentry(nestingLevel, new ClassTypeNode(new ArrayList<>(clone.allFields), new ArrayList<>(clone.methods)),decOffset--);
			} else {
				entry = new STentry(nestingLevel, new ClassTypeNode(new ArrayList<>(), new ArrayList<>()) ,decOffset--);
			}
			//inserimento di ID nella symtable
			if (hm.put(n.id, entry) != null) {
				System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
			
			Map<String, STentry> virtualTable;
			if(n.superID != null) {
				virtualTable = new HashMap<>(classTable.get(n.superID));
			} else {
				virtualTable = new HashMap<>();
			}
			classTable.put(n.id, virtualTable);
			
			//creo un nuovo livello per la symbol table
			nestingLevel++;
			symTable.add(virtualTable);
			
			int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
			decOffset=n.superID!=null ? ((ClassTypeNode)entry.type).allMethods.size() : 0;
			int fieldsOffset= n.superID!=null ? -((ClassTypeNode)entry.type).allFields.size()-1 : -1;
			
			for (FieldNode f : n.fields) {
				if(optimizer.contains(f.id)) {
					System.out.println("Field id " + f.id + " already declared in class "+n.id);
					stErrors++;
				} else {
					optimizer.add(f.id);
				}
				if(virtualTable.containsKey(f.id)) {
					if(virtualTable.get(f.id).type instanceof MethodTypeNode) {
						System.out.println("Cannot override method id " + f.id + " with a field at line "+ n.getLine());
						stErrors++;
					} else {
						//sostituisco nuova STentry alla vecchia preservando l’offset che era nella vecchia STentry
						f.offset = virtualTable.get(f.id).offset;
						virtualTable.put(f.id, new STentry(nestingLevel,f.getType(),f.offset));
						((ClassTypeNode)hm.get(n.id).type).fields.set(-f.offset-1, f.getType());
					}
				} else {
					((ClassTypeNode)hm.get(n.id).type).fields.add(f.getType());
					f.offset = fieldsOffset;
					virtualTable.put(f.id, new STentry(nestingLevel,f.getType(),fieldsOffset--));
				}
			}
			
			for (int i=0; i<n.methodlist.size(); i++) {
				if(optimizer.contains(n.methodlist.get(i).id)) {
					System.out.println("Method id " + n.methodlist.get(i).id + " already declared in class "+n.id);
					stErrors++;
				} else {
					optimizer.add(n.methodlist.get(i).id);
				}
				visit(n.methodlist.get(i));
				if(n.methodlist.get(i).offset < ((ClassTypeNode)hm.get(n.id).type).allMethods.size()) {
					((ClassTypeNode)hm.get(n.id).type).allMethods.set(n.methodlist.get(i).offset, (MethodTypeNode) n.methodlist.get(i).getType());
				} else {				
					((ClassTypeNode)hm.get(n.id).type).allMethods.add( (MethodTypeNode) n.methodlist.get(i).getType());
				}
			}
			
			//rimuovere la hashmap corrente poiche' esco dallo scope               
			symTable.remove(nestingLevel--);
			decOffset = prevNLDecOffset;
			return null;
		}	
	/*
	public Void visitNode(FieldNode node) {
		return null;
	}
	*/
	public Void visitNode(MethodNode n){
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
		
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
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
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	public Void visitNode(ClassCallNode node){
		if (print) printNode(n);
		STentry entry = stLookup(n.classID);
		if (entry == null) {
			System.out.println("Class id " + n.classID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
		}
		STentry methodEntry = classTable.get(((RefTypeNode)entry.type).id).get(n.methodID);
		if (methodEntry == null) {
			System.out.println("Method id " + n.methodID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.methodEntry = methodEntry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
	
	public Void visitNode(NewNode n) {
		if (print) printNode(n);
		if(!classTable.containsKey(n.id)) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			STentry entry = symTable.get(0).get(n.id);
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	public Void visitNode(EmptyNode n){
		if (print) printNode(n, "null");
		return null;
	}

	/*Tipo in STentry per metodi è MethodTypeNode
	 *– per distinguere ID di funzioni da ID di metodi, che
	 *richiedono uso dispatch table quando vengono invocati
	 */
	public Void visitNode(MethodTypeNode n) {
		final ArrowTypeNode fun;		//tipo funzionale
		return null;
	}
	public Void visitNode(RefTypeNode n) {
		return null;
	}
	public Void visitNode(EmptyTypeNode n) {
		if (print) printNode(n, "null");
		return null;
	}
	
	/* STentry per i nomi delle Classi
	 * Nesting level è 0 (ambiente globale)
	 * • Offset: da -2 decrementando ogni volta che si
	 * incontra una nuova dichiarazione di classe
	 * – in base alla sintassi, dichiarazioni di funzioni/variabili
	 * appaiono in seguito nell’ambiente globale
	 * 
	 * Quando si visita lo scope interno di una classe,
	 * la Symbol Table per il livello corrispondente
	 * (livello 1 da noi) deve includere anche le
	 * – STentry per i simboli (metodi e campi) ereditati su
	 * cui non è stato fatto overriding
	 * • Per questo motivo tale tabella viene chiamata Virtual Table
	 */
	public Void visitNode(ClassTypeNode n) {
		ArrayList<TypeNode> allFields;			//tipi dei campi, inclusi quelli ereditati, in ordine di apparizione
		ArrayList<ArrowTypeNode> allMethods;	//tipi funzionali metodi, inclusi ereditati, in ordine apparizione
		return null;
	}
		
///////////////////////////////////////////////////////////////////////////
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
}
