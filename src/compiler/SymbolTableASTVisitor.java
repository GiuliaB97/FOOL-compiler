package compiler;

import java.util.*;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	/*
	 * Ho una lista di tabelle organizzata per scope ambiente globale nesting level 0 la mappa a indice nesting level è quella in cui siamo attualmente.
	 * quando entro in uno scope aumento il nesting level e quando esco lo decremento
	 * 
	 * Quando faccio qualcosa con la symbol table : quando incontro dichiarazioni/usi entro/esco da scope:
	 * 
	 * se ho un prog node: programma senza dichairazione non faccio nulla continuo semplicemente la mia visita uguale se incontro un intero, times, plus, bool ecc.
	 * 
	 * Questa visita torna void come la print perchè il suo obiettivo è arricchire l'albero quando incontra un uso che fa match con una dichiarazione 
	 * symbol table: lista di mappe; ogni mappa mappa nomi di identificatori a stentry: 
	 * che possono contenere varie cose per il momento iniziamo col nesting level
	 */
	private int nestingLevel=0; //Nesting level attuale: parte da zero ambiente globale
	private int decOffset=-2; /*counter for offset of local declarations at current nesting level 
								parte da -2 e lo decommento ogni volta che incontro una nuova dichiarazione (di classe) 
								*/
	int stErrors=0;
/////////////////////////////////////
	private Map<String, Map<String,STentry>> classTable = new HashMap<>();
	private Set<String> hs;
//////////////////////////////////////
	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging
	/* stLookup
	 * Overview:
	 * 		metodo che data l'id della var la va a cercare nella symboltable 
	 * 		e ritorna la pallina se la trova e null altrimenti
	 * A. continuo la ricerca fino alla tabella finchè 
	 * 		non trovo la dichiarazione della var che sto cercando
	 * B. mi da inizialmente la tabella a nestinglevel e 
	 * 		cerco di farmi dare la var che sto cercando
	 * C. se quando esco entry è ancora null allora 
	 * 		non ha trovato la pallina al contrario l'ha trovata
	 */
	private STentry stLookup(String id) {
		int j= this.nestingLevel; 					//parto dal nesting level in cui sono 
		STentry entry  =null;
		while (j>=0 && entry==null) {				/*A */
			entry=this.symTable.get(j--).get(id); 	/*B */
		}
		return entry;								/*C*/
	}
	
	/* la visita torna void perchè fa solo visite se riesce ad associare id 
	 * ad uso allora attacca la pallina all'albero
	 */

	/*visitNode
	 * Overview:
	 * Se sono nel corpo principale del programma
	 * (questa è la radice quando ho un LET IN nel prog body)
	 * Cosa faccio in questo caso?
	 * COsa faccio con le dichairazioni che vedo come figli?
	 * QUi in pratica stiamo entrando nello scope dell'ambiente globale, 
	 * la variabile nesting level parte da 0 quindi va già bene 
	 * ma devo creare la tabella per l'ambiente globale
	 * 
	 * A. tabella che conterrà le dichiarazioni dell'ambiente globale
	 * B.aggiungo la tabella alla mia symbol table
	 * C.visito le dichiarazioni contenute nella let in
	 * D.visito il corpo che userà le dichiarazioni
	 * E.ide ala hashmap cresce quando entro in scope e 
	 * 	decresce quando esco quando finisce il programma posso buttare via tutto 
	 * (rimuovo quella dell'abiente globale perchè le altre teoricamente le ho già rimosse tutte)
	 * 
	 */
	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry>hm= new HashMap<>();	//A
		symTable.add(hm); 							//B
		for (Node dec : n.declist) visit(dec);		//C													
		visit(n.exp);								//D
		symTable.remove(0);							//E
		return null;
	}

	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	
	/* visitNode
	 * Overview:
	 * 	Inserisco nel fronte della tabella la dichiarazione della funzione
	 * 	In questo caso andiamo come prima ad inserire il nome della funzione 
	 * 	(dopo averne incontrato la dichiarazione)n nel fronte della lista 
	 * A. mi da la tabella dello scope corrente					Map<String, STentry>hm=this.symTable.get(nestingLevel); //A
	 * B. Creo un anuova pallina								STentry entry = new STentry(nestingLevel);				//B
	 * C. inserimento id nella symboltable, ma devo controllare se c'era già
	 *		 (in Java il metodo put controlla se la chiave c'era già se put torna null 
	 *		 la chiave non esisteva se c'era già ritorna il valore della vecchia chiave
	 *		 NB ora n.id= nome funzione							if(hm.put(n.id, entry)!=null) {							//C
	 * D. ora devo entrare in un nuovo scope; incremento nesting level + creo una nuova 
	 * 		hashmap per il nuovo scope
	 * E. tabella che conterrà le dichiarazioni dell'ambiente globaleMap<String, STentry>nhm= new HashMap<>();	// E
	 * F. aggiungo la tabella alla mia symbol table				symTable.add(nhm); 							// F
	 * G. visito le dichiarazioni di variabili che possono essere variabili 
	 * 		o funzioni											for (Node dec : n.declist) visit(dec);		// G
	 * H. visito il corpo della mia funzione che può usare cose locali o cose in nesting 
	 * 		level inferiori (finoa d arrivare a zero)			visit(n.exp);								// H
	 * I. prima di terminare devo uscire dallo scope			this.symTable.remove(nestingLevel--);		// I
		
	 */
	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		n.setType(new ArrowTypeNode(parTypes, n.retType));												//new line
		
		STentry entry = new STentry(nestingLevel, 
				new ArrowTypeNode(parTypes,n.retType),decOffset--);
		decOffset--;																					//new line
		
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
		for (ParNode par : n.parlist) {
			if(par.getType() instanceof ArrowTypeNode) parOffset++;
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	/*
	 * Inserisco nel fronte della tabella la dichiarazione della variabile
	 * A. vado ad inserire nella tabella che sta al fronte della lista l'id della variabile , 
	 * 		ma devo vedere se c'è già questo lavoro va fatto prima o dopo la visita a exp? Prima.
	 * B. exp è il copoche userà le dichiarazioni 
	 * C. mi da la tabella dello scope corrente
	 * D. Creo un anuova pallina
	 * E. inserimento id nella symboltable, ma devo controllare se c'era già 
	 * 		(in Java il metodo put controlla se la chiave c'era già 
	 * 			se put torna null la chiave non esisteva se c'era già ritorna il valore della vecchia chiave
	 */	
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);//B
		Map<String, STentry> hm = symTable.get(nestingLevel);//C
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		if(n.getType() instanceof ArrowTypeNode) decOffset--;
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {//E
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
	
	@Override
	public Void visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(LessEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(NotNode n) {
		if (print) printNode(n);
		visit(n.val);
		return null;
	}

	@Override
	public Void visitNode(DivNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(MinusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(OrNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(AndNode n) {
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
		
		for (int i=0; i<n.methods.size(); i++) {						//Stesso discorso dei campi
			if(!hs.contains(n.methods.get(i).id)) {
				hs.add(n.methods.get(i).id);
			} else {
				System.out.println("Method id " + n.methods.get(i).id + " already declared in class "+n.id);
				stErrors++;
			}
			visit(n.methods.get(i));
			if(n.methods.get(i).offset < ((ClassTypeNode)hm.get(n.id).type).allMethods.size()) {//aggiorno allMethods settando la posizione offset al tipo funzionale (primo metodo offset 0)
				((ClassTypeNode)hm.get(n.id).type).allMethods.set(n.methods.get(i).offset, (MethodTypeNode) n.methods.get(i).getType());
			} else {				
				((ClassTypeNode)hm.get(n.id).type).allMethods.add( (MethodTypeNode) n.methods.get(i).getType());
			}
		}          
														// all'uscita della dichairazione di classe
		symTable.remove(nestingLevel--);				// viene rimosso il livello di VT corrente 
		decOffset = prevNLDecOffset;					// e ripristinato l'offset
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
	public Void visitNode(ClassCallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.classID);
		if (entry != null) {//cercata come in IdNode e CallNode
			n.entry = entry;
		} else {
			System.out.println("Class id " + n.classID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		STentry methodEntry = classTable.get(((RefTypeNode)entry.type).id).get(n.methodID);
		if (methodEntry != null) {//cercata nella VT (raggiunta tramite CT della classe del tipo RefTypeNode)
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
		if(classTable.containsKey(n.id)) {//ID deve essere in CT e STentry presa direttamente da livello 0 della ST
			STentry entry = symTable.get(0).get(n.id);
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