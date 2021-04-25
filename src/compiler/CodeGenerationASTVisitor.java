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

  private static List<List<String>> dispatchTables;//ne ho una per ogni classe indrizzi di tutti i metodi anche ereditati --> etichette; Ricorda dispatch table nello heap
  													//la istanzio in progLetInNode perchè? perchè mi serve per le dichiarazioni, se un programma non ha dichiarazioni non mi serve(?)
  													//NB funNode modified per la gestione dei tipi funzionali (offset doppio)
  //Dispatch pointer in AR dell'ambiente globale--> è reperibile all'offset della classe-->valore iniziale fp 
	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		dispatchTables = new ArrayList<>();									//inizializzazione dispatch table; la faccio qui perchè
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

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
			if( ((DecNode)dec).getType() instanceof ArrowTypeNode) {				//this is new
				popDecl = nlJoin(popDecl,"pop");									//this is new
			}																		//this is new
		}
		for (int i=0;i<n.parlist.size();i++) {
			popParl = nlJoin(popParl,"pop");
			if(n.parlist.get(i).getType() instanceof ArrowTypeNode) {				//this is new
				popParl = nlJoin(popParl,"pop");									//this is new
			}																		//this is new
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

	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}
	
	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),
			"push 1",
			"beq "+l1,
			visit(n.el),
			"b "+l2,
			l1+":",
			visit(n.th),
			l2+":"
		);
	}

	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"				
		);
	}
	
	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}
	////////////////////
	//Language extension
	@Override
	public String visitNode(LessEqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();		//numeri uguali, o primo minore del secondo
	 	String l2 = freshLabel();		//Secondo maggiore del primo
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"bleq "+l1,					//bleq: salta se il primo è minore uguale del secondo
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	@Override
	public String visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.right),				//non avendo una bgeq: semplicemnte inverto l'ordine in cui metto i valori sullo stack
			visit(n.left),
			"bleq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	@Override
	public String visitNode(DivNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"div"
		);	
	}
	
	@Override
	public String visitNode(MinusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"minus"
		);	
	}
	//Idea: 1 - n.exp dove n.exp è vincolato ad essere 0 o 1
	@Override
	public String visitNode(NotNode n) {
		if (print) printNode(n);
		return nlJoin(
			"push 1",
			visit(n.val),	 
			"sub"					
		);	
	}
	
	// Idea:considera l'aritmetica binaria 0*0=0; 0*1=0, ecc.
	@Override
	public String visitNode(AndNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"					
		);	
	}
	
	@Override
	public String visitNode(OrNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();		//risultato = 1
		String l2 = freshLabel();		//risultato = 0
		return nlJoin(
			visit(n.left),
			"push 1",			
			"beq " + l1,			//se il primo elemento è uno non controllo nemmeno il secondo; restituisco direttamente uno
			 visit(n.right),		//se il primo elemento era zero allora controllo il secondo; se è uno restituisco uno
			"push 1",
			"beq"+l1+":",
			"push 0",				//sono obbligata a mettere lo zero, perchè sullo stack ci avevo messo un uno per fare il controllo prima
			"b"+ l2,				// b:salto incondizionato (ho già messo uno 0 allo step precedente non devo fare nulla posso solo tornare.
			l1+":",
			"push 1",
			l2+":"	
		);	
	}
	////////////////////////////////////////
	@Override
	public String visitNode(ClassNode n) {
		if (print) printNode(n,n.id);
		if(n.superID!=null) {
			dispatchTables.add(new ArrayList<>(dispatchTables.get(-n.superEntry.offset-2))); // devo copiare tutta la dispatch table della classe da cui estendo ; 	la individuo in base a offset classe da cui eredito in "superEntry"per layout ambiente globale: posizione -offset-2 	di dispatchTables
		} else {			
			dispatchTables.add(new ArrayList<>());
		}
		
		for(MethodNode m:n.methods) {
			visit(m);
			if(m.offset >= dispatchTables.get(dispatchTables.size()-1).size()) {
				dispatchTables.get(dispatchTables.size()-1).add(m.label);
			} else {				
				dispatchTables.get(dispatchTables.size()-1).set(m.offset, m.label);
			}
		}
		String dispatchTablesOnHeap = null;
		for(String s: dispatchTables.get(dispatchTables.size()-1)) {
			dispatchTablesOnHeap = nlJoin(
					dispatchTablesOnHeap,
					"push "+s,
					"lhp", 		
					"sw",		//in this way I am writing the string s on the heap
					"lhp",
					"push 1",
					"add",
					"shp"		//take hp value, increment it, and put it in hp		
					);
		}
		
		return nlJoin(
				"lhp",
				dispatchTablesOnHeap
				);		
	}
	
	@Override
	public String visitNode(EmptyNode n) {
		if (print) printNode(n);
		return "push -1";//-1 è un valore diverso da qualsiasi object pointer di qualsiasi oggetto creato
	}
	//CONTROLLA SE DEVI FAR EUNA O DUE POP
	/*
		MethodNode
		– genera un’etichetta nuova per il suo indirizzo e la
		mette nel suo campo "label" (aggiungere tale campo)
		– genera il codice del metodo (invariato rispetto a
		funzioni) e lo inserisce in FOOLlib con putCode()
		– ritorna codice vuoto (null)
	 */
	@Override
	public String visitNode(MethodNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
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
				"sfp", 			// set $fp to popped value (Control Link)
				"ltm", 			// load $tm value (function result)
				"lra", 			// load $ra value
				"js"  			// jump to to popped address
			)
		);
		return "";	//torno null
	}
		
	@Override
	public String visitNode(ClassCallNode n) {
		if (print) printNode(n,n.methodID);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", 			// load Control Link (pointer to frame of function "id" caller)
			argCode, 		// generate code for argument expressions in reversed order
			"lfp", getAR, 	// retrieve address of frame containing "id" declaration
                          	// by following the static chain (of Access Links)
			
			"push "+n.entry.offset,
			"add",
			"lw",			//load value of id variable
			
			"stm",
			"ltm",			//duplicate the value
			
			"ltm",
			"lw",			//follow the access link
			"push "+n.methodEntry.offset,
			"add",
			"lw",			//retrieve the address of the called method
			"js"			//jump to the method
		);
	}

	/*
	 * NewNode new ID()
	 * – prima:
	 * • si richiama su tutti gli argomenti in ordine di apparizione
	 * (che mettono ciascuno il loro valore calcolato sullo stack)
	 * – poi:
	 * • prende i valori degli argomenti, uno alla volta, dallo stack e li
	 * mette nello heap, incrementando $hp dopo ogni singola copia
	 * • scrive a indirizzo $hp il dispatch pointer recuperandolo da
	 * contenuto indirizzo MEMSIZE + offset classe ID
	 * • carica sullo stack il valore di $hp (indirizzo object pointer 
	 * da ritornare) e incrementa $hp
	 * – nota: anche se la classe ID non ha campi l’oggetto
	 * allocato contiene comunque il dispatch pointer
	 * • == tra object pointer ottenuti da due new è sempre falso
	 */
	@Override
	public String visitNode(NewNode n) {
		if (print) printNode(n,n.id);
		String argCode = null, putArgsOnHeap = null;
		for (int i=0 ; i<n.arglist.size() ; i++) {
			argCode=nlJoin(argCode,visit(n.arglist.get(i)));
			putArgsOnHeap = nlJoin(putArgsOnHeap, 
					"lhp", 		
					"sw",		//in this way I am writing the value on the stack on the heap
					
					"lhp",
					"push 1",
					"add",
					"shp"		//take hp value, increment it, and put it in hp
					);
		}
		return nlJoin(
				argCode,
				putArgsOnHeap,
				
				"push "+(ExecuteVM.MEMSIZE+n.entry.offset),
				"lw",
				"lhp",
				"sw",		//write in hp the dispatch pointer
				
				"lhp",		//copy object pointer (to be returned) on the stack
				
				"lhp",
				"push 1",
				"add",
				"shp"		//increment hp
				);
	}
	/*
	 * CallNode ID()
	 * – codice ritornato modificato: due cose recuperate 
	 * come valori dall'AR dove è dichiarato l'ID con
	 * meccanismo usuale di risalita catena statica
	 * • indir (fp) ad AR dichiaraz. funzione (recuperato a offset ID)
	 * 		– usato per settare nuovo Access Link (AL)
	 * • indir funzione (recuperato a offset ID - 1)
	 * – usato per saltare a codice funzione
	 * 
	 * VERSIONE OO
	 * CallNode ID()
	 * – controllo se ID è un metodo (tipo "MethodTypeNode" in STentry)
	 * • se non lo è, invariato
	 * • se lo è, modificato:
	 * 		– dopo aver messo sullo stack l’Access Link impostandolo
	 * 			all’indirizzo ottenuto tramite risalita della catena statica
	 * 			(in base a differenza di nesting level di ID) e aver
	 * 			duplicato tale indirizzo sullo stack
	 * 				· fin qui il codice generato è invariato
	 *	 			· si noti che in questo caso tale indirizzo è l’object pointer
	 * 		– recupera (usando l’offset di ID nella dispatch table
	 * 			riferita dal dispatch pointer dell’oggetto) l'indirizzo del metodo a cui saltare
	 */
	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", 			// load Control Link (pointer to frame of function "id" caller)
			argCode, 		// generate code for argument expressions in reversed order
			"lfp", 			//load object ar (if method) or first ring of the chain (if function)
			
			//change code if 
			n.entry.type instanceof MethodTypeNode 
				? nlJoin(
						"lw",
						"stm", 			// set $tm to popped value (with the aim of duplicating top of stack)
						"ltm", 			// load Access Link (pointer to frame of function "id" declaration)
						"ltm", 			// duplicate top of stack
						"lw",
						"push "+n.entry.offset,
						"add",
						"lw",
						"js"
						)
				: nlJoin(
						getAR, 			// retrieve address of frame containing "id" declaration
                      					// by following the static chain (of Access Links)
						"push "+n.entry.offset, 
						"add",
						"stm", 			
						"ltm", 			
						"lw",
						"ltm", 			
						"push 1", 
						"sub", 			
						"lw", 			// load address of "id" function
						"js"  			// jump to popped address (saving address of subsequent instruction in $ra)
						)
			);
	}
	/*HO EXTENSION
	 * IdNode ID
	 * - se il tipo non è funzionale, ritorna codice invariato
	 * – se lo è, due cose sono messe nello stack,
	 * recuperandole come valori dall'AR dove è dichiarato
	 * l'ID con meccanismo usuale di risalita catena statica; 
	 * nell'ordine:
	 * 1. indir (fp) ad AR dichiaraz. funzione (recuperato a offset ID)
	 * 2. indir funzione (recuperato a offset ID - 1)
	 */
//invariato non ci importa se risalendo la catena statica giunga ad AR in stack o a un oggetto in heap comunque prendo il valore che c'è all'offset della SEntry
	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");		
		String ret = nlJoin(
				"lfp", 
				getAR,  // retrieve address of frame containing "id" declaration by following the static chain (of Access Links)
				"push "+n.entry.offset, 
				"add" // compute address of "id" declaration
			);
		
		//se la variabile è una funzione bisogna recuperarne l'indirizzo a offset id - 1
		if(n.entry.type instanceof ArrowTypeNode) {
			ret = nlJoin(
					ret,	//ha l'indirizzo di della dichiarazione di id (lo devo salvare)
					"stm",	//set $tm to popped value (with the aim of duplicating top of stack)
					"ltm",
					"lw",
					"ltm",	//
					"push 1",
					"sub"
					);
		} 
		return nlJoin(ret, "lw");
	}
}