package compiler.exc;

import compiler.AST.*;
import compiler.lib.*;
import svm.ExecuteVM;
import compiler.exc.*;
import static compiler.lib.FOOLlib.*;

import java.util.ArrayList;
import java.util.List;
/*
 * CODE GENERATION
 * 
 * OO
 * • Quando si genera il codice per la dichiarazione di una classe viene creata 
 * 		la sua Dispatch Table (seguendo le regole spiegate a lezione)
 * • Il codice generato la alloca nello heap e mette il relativo dispatch pointer
 * 		 in AR dell’amb. globale
 * 		– sarà reperibile all’offset della classe
 * • E’ quindi comodo accedere direttamente ad indirizzo (fp) 
 * 		dell’AR dell’ambiente globale
 * 		– tale indirizzo in base a nostro layout dell’ambiente globale 
 * 			è costante MEMSIZE (valore iniziale di $fp)
 * 1.Struttura Dati per Dispatch Tables
 * • Per ogni classe si costruisce la relativa Dispatch Table 
 * 		(un ArrayList di String)
 * 		– etichette (indirizzi) di tutti i metodi, anche ereditati, 
 * 			ordinati in base ai loro offset
 * 				• cioè stesso ordine di allMethods nel ClassTypeNode 
 * • Le Dispatch Table di tutte le classi vengono create staticamente 
 * 		dal compilatore
 * 		– in campo privato dispatchTables del visitor
 * 			• List< List<String> > dispatchTables
 * – in ordine di dichiarazione classi nell’ambiente globale
 * 2.Dichiarazioni
 * • FieldNode – non usato (come ParNode)
 * • MethodNode -uguale al codice delle funzioni prima del cambiamento per la versione HO
 * 	– genera un’etichetta nuova per il suo indirizzo e la mette nel suo 
 * 			campo "label" (aggiungere tale campo)
 * 	– genera il codice del metodo (invariato rispetto a funzioni) 
 * 		e lo inserisce in FOOLlib con putCode()
 *	– ritorna codice vuoto (null)
 * • ClassNode
 *	– ritorna codice che alloca su heap la dispatch table della classe e 
 *		lascia il dispatch pointer sullo stack,
 *	– ciò viene fatto come descritto in seguito:
 *		Dichiarazione Classe: costruzione Dispatch Table
 *		1. aggiungo una nuova Dispatch Table a dispatchTables
 *			– se non si eredita, essa viene inizialmente creata vuota
 *			– altrimenti, viene creata copiando la Dispatch Table della classe
 *				da cui si eredita (si deve creare copia di tutto il contenuto
 *				della Dispatch Table e non copiare il solo riferimento)
 *				• la individuo in base a offset classe da cui eredito in "superEntry"
 *					– per layout ambiente globale: posizione -offset-2 
 *						di dispatchTables
 *		2. considero in ordine di apparizione i miei figli metodi
 *			(in campo methods) e, per ciascuno di essi,
 *			– invoco la sua visit()	
 *			– leggo l’etichetta a cui è stato posto il suo codice dal suo campo
 *				"label" ed il suo offset dal suo campo "offset"
 *			– aggiorno la Dispatch Table creata settando la posizione data
 *				dall’offset del metodo alla sua etichetta
 *		Dichiarazione Classe: codice ritornato
 *		1. metto valore di $hp sullo stack: sarà il dispatch pointer da ritornare alla fine
 *		2. creo sullo heap la Dispatch Table che ho costruito: la
 *			scorro dall’inizio alla fine e, per ciascuna etichetta,
 *			– la memorizzo a indirizzo in $hp ed incremento $hp
 *
 *Espressioni: codice ritornato
 *• EmptyNode null
 *	– mette sullo stack il valore -1
 *		• sicuramente diverso da object pointer di ogni oggetto creato
 *• IdNode ID
 *	– invariato
 *		• indipendentemente dal fatto che, risalendo la catena statica,
 *			giunga ad AR in stack o ad oggetto in heap comunque prendo
 *			il valore che c'è all'offset della STentry
 *
 *• ClassCallNode ID1.ID2()
 *	– inizia la costruzione dell’AR del metodo ID2 invocato:
 *		• dopo aver messo sullo stack il Control Link e il valore dei parametri
 *			– fin qui il codice generato è invariato rispetto a CallNode
 *		• recupera valore dell'ID1 (object pointer) dall'AR dove è dichiarato 
 *			con meccanismo usuale di risalita catena statica (come per IdNode) 
 *			e lo usa:
 *			– per settare a tale valore l’Access Link mettendolo sullo stack e, 
 *				duplicandolo,
 *			– per recuperare (usando l’offset di ID2 nella dispatch table 
 *				riferita dal dispatch pointer dell’oggetto) l'indirizzo del metodo 
 *				a cui saltare
 *• CallNode ID()
 *	– controllo se ID è un metodo (tipo "MethodTypeNode" in STentry)
 *		• se non lo è, invariato
 *		• se lo è, modificato:
 *			– dopo aver messo sullo stack l’Access Link impostandolo
 *				all’indirizzo ottenuto tramite risalita della catena statica
 *				(in base a differenza di nesting level di ID) e aver duplicato 
 *				tale indirizzo sullo stack
 *				· fin qui il codice generato è invariato
 *				· si noti che in questo caso tale indirizzo è l’object pointer
 *			– recupera (usando l’offset di ID nella dispatch table riferita dal 
 *				dispatch pointer dell’oggetto) l'indirizzo del metodo a cui saltare
 *• NewNode new ID()
 *	– prima:
 *		• si richiama su tutti gli argomenti in ordine di apparizione
 *			(che mettono ciascuno il loro valore calcolato sullo stack)
 *	– poi:
 *		• prende i valori degli argomenti, uno alla volta, dallo stack e li
 *			mette nello heap, incrementando $hp dopo ogni singola copia
 *		• scrive a indirizzo $hp il dispatch pointer recuperandolo da
 *			contenuto indirizzo MEMSIZE + offset classe ID
 *		• carica sullo stack il valore di $hp (indirizzo object pointer
 *			da ritornare) e incrementa $hp
 *	– nota: anche se la classe ID non ha campi l’oggetto allocato contiene 
 *		comunque il dispatch pointer
 *		• == tra object pointer ottenuti da due new è sempre falso! 
 */
public class ACodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {
	private static List<List<String>> dispatchTables = new ArrayList<>();	/*EX OO: Le Dispatch Table di tutte le classi vengono create staticamente dal compilatore in campo privato dispatchTables del visitor
	 * tipo richiesto da specifica
	 */

	ACodeGenerationASTVisitor() {}
	ACodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		dispatchTables = new ArrayList<>();
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
	/*	HO EXTENSION
	 * FunNode
	 * – codice ritornato: due cose sono messe nello stack, nell'ordine
	 * 		1. indir (fp) a questo AR (in reg $fp)
	 * 		2. (finisce a offset-1) indir della funzione (etichetta generata)
	 * – codice della funzione:
	 * 		• in caso tra i parametri o le dichiarazioni vi siano ID di tipo
	 * 			funzionale (usare getType() su DecNode) si devono
	 * 			deallocare due cose dallo stack (con due "pop")
	 */
	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
			if( ((DecNode)dec).getType() instanceof ArrowTypeNode) {
				popDecl = nlJoin(popDecl,"pop");
			}
		}
		for (int i=0;i<n.parlist.size();i++) {
			popParl = nlJoin(popParl,"pop");
			if(n.parlist.get(i).getType() instanceof ArrowTypeNode) {
				popParl = nlJoin(popParl,"pop");
			}
		}
		String funl = freshFunLabel();
		putCode(
				nlJoin(
						funl+":",
						"cfp", // set $fp to $sp value
						"lra", // load $ra value
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for function body expression
						"stm", // set $tm to popped value (function result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link)
						"ltm", // load $tm value (function result)
						"lra", // load $ra value
						"js"  // jump to to popped address
						)
				);
		return "push "+funl;		
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
	////////////////////////////////////////////////////LANGUAGE EXTENSION NODES
	@Override
	public String visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.right),
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
	public String visitNode(LessEqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"bleq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
				);
	}

	@Override
	public String visitNode(NotNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.val), // complemento a 2 nego il numero moltiplicandolo per -1 e poi risommo 1 per ottenere il numero corretto
				"push -1", 
				"mult", 
				"push 1",
				"add"
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

	@Override
	public String visitNode(OrNode n) {//Dubito seriamente funzioni
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"beq "+l1,	
				"push 1",
				l1+":",
				"push 0",
				"beq"+l2,
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
	public String visitNode(AndNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"mult"				//0*0=0; 0*1=0, ecc. => considera l'aritmetica binaria		
				);
	}

	//////////////////////////////////////////////////////////////
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
		String retBasic = nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp");
		if(!(n.entry.type instanceof MethodTypeNode )) { //se ID non è methodTypeNode allora torno la versione HO 
			return nlJoin(
					retBasic,
					getAR, // retrieve address of frame containing "id" declaration
					// by following the static chain (of Access Links)
					"push "+n.entry.offset, 
					"add",

					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"lw", // load address of "id" function	            
					"ltm", // duplicate top of stack
					"push 1", 
					"sub", 	
					"lw", // load address of "id" function
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)

					);	
		}else {//altrimenti versione OOP 
			return nlJoin(
					retBasic,	
					"lw", // load address of "id" function
					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"lw", // load address of "id" function

					"push "+n.entry.offset, 
					"add",
					"lw", // load address of "id" function
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
					);
		}
	}
	@Override
	public String visitNode(ClassCallNode n) {
		if (print) printNode(n,n.methodId);
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
/*		if (print) printNode(n);

		List<String> dispatchTable;		//creo la sua dispatch table per ogni classe; tipo di variabile richiesto da specifica
	
		//ora devo aggiungere la nuova dispatch table alla lista delle dispatch tables
		if(n.superId == null) {// se non eredito la creo vuota
			dispatchTable = new ArrayList<>();
		}else {//se eredito devo copiare la dispatch table della super class (tutta, non solo il riferimento); come la individuo? la trovo in base all'offset della classe, da cui eredito in superentry
			dispatchTable = new ArrayList<>(dispatchTables.get(-n.superEntry.offset-2)); //layout dell'ambiente globale si trova:  dispatchtables[(superEntry) -offset-2 ]; con questa operazione converto l'offset nell AR globale in indice da 0 a ...
		}

		for(MethodNode m : n.methodlist) {	//considero in ordine di apparizione i metodi figli (campo methods)
			visit(m);					//su ognuno invoco la visit
			
			//leggo l'etichetta a cui è stato posto il codice e il suo offset dai relativi campi
			if( m.offset >= dispatchTable.size() || dispatchTable.get(m.offset).isEmpty() ) {
				dispatchTable.add(m.label); 		 // normalmente si usa l'etichetta del metodo della super-classe 
			}else {//aggiorno la dispatch table creata settando la posizione data dall'offset ed etichetta
				dispatchTable.set(m.offset,m.label); // overriding: si usa l'etichetta del metodo della classe che estende
			}
		}
		dispatchTables.add(dispatchTable);	//aggiungo la nuova dispatch table 

		String methodCode = null;	
														//devo creare sullo heap la dispatch table construita. 	
		for (String m : dispatchTable) {				//Come? la scorro tutta
			methodCode = nlJoin(						//per ogni metodo devo mettere l'etichetta sull'heap
						methodCode,
						"push " + m, 					// push della label del metodo considerato
						"lhp", 		 					// push di hp sullo stack, 
														// ATTENZIONE: hp = attuale indirizzo  dell'heap, 
														//					è dove devo scrivere la label
						"sw", 		 					// scrivo la label all'indirizzo puntato da hp
						"lhp", "push 1", "add", "shp"	// incremento hp per puntare alla succesiva posizione dell'heap
						); 
		}
		return nlJoin(
				"lhp", 			// metto il valore di hp sullo stack; è il dispatch pointer da tornare
				methodCode  	// metto sullo stack il codice per generare la dispach table sull'heap 
				);
				*/
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
	public String visitNode(NewNode n) {
		if (print) printNode(n,n.id);
		String arg=null;
		String heap=null;
		for (int i=0 ; i<n.arglist.size() ; i++) {// si richiama su tutti i suoi argomenti in ordine di apparizione 
												//e mette il loro valore sullo stack 
			arg=nlJoin(arg,visit(n.arglist.get(i)));	//salvo tutti gli argomenti
			heap = nlJoin(					 // codice per copiare ogni argomento sull'heap
					heap,
					"lhp", 		// carico sullo stack indirizzo heap pointer
					"sw", 		// scrivo all'indirizzo di hp l'argomento / campo 
					"lhp", "push 1", "add", "shp" // incremento hp di 1 dopo ogni copia
					);
		}
		return nlJoin(
				arg,	//carico gli argomenti tutti sullo stack
				heap,	// sposto gli argomenti nell'heap, sono i campi dell'oggetto 
				"push "+(ExecuteVM.MEMSIZE+n.entry.offset), "lw",//recupero da qui il dispatch pointer; carico sullo stack l'indirizzo della dispatch table (prendendolo dalla dichiarazione della classe sull'AR base nello stack)
					
				"lhp","sw",	// scrivo l'indirizzo della dispatch table all'indirizzo di hp (cioè lo scrivo sull'heap subito dopo/prima dei campi)
				
				"lhp",		// valore lasciato sullo stack al termine di tutto il new(): carico sullo stack il valore di HP che contiene l'object pointer
				
				"lhp", "push 1","add",	"shp"		//increment hp
				);
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
public String visitNode(MethodNode n) {//Codice uguale identico a come era quello delle funzioni prima dell'estensione HO
	if (print) printNode(n,n.id);
	String declCode = null, popDecl = null, popParl = null;
	for (Node dec : n.declist) {
		declCode = nlJoin(declCode,visit(dec));
		popDecl = nlJoin(popDecl,"pop");
	}

	for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
	String funl = freshFunLabel();
	putCode(
			nlJoin(
					funl+":",
					"cfp", // set $fp to $sp value
					"lra", // load $ra value
					declCode, // generate code for local declarations (they use the new $fp!!!)
					visit(n.exp), // generate code for function body expression
					"stm", // set $tm to popped value (function result)
					popDecl, // remove local declarations from stack
					"sra", // set $ra to popped value
					"pop", // remove Access Link from stack
					popParl, // remove parameters from stack
					"sfp", // set $fp to popped value (Control Link)
					"ltm", // load $tm value (function result)
					"lra", // load $ra value
					"js"  // jump to to popped address
					)
			);
	return "push "+funl;	
}

@Override
public String visitNode(EmptyNode n) {
	return nlJoin("push -1"); 	//-1 è un valore diverso da qualsiasi object pointer di qualsiasi oggetto creato
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
@Override
public String visitNode(IdNode n) {
	if (print) printNode(n,n.id);
	String getAR = null;
	for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
	String retBasic= nlJoin(	
			"lfp", getAR, // retrieve address of frame containing "id" declaration
			// by following the static chain (of Access Links)
			"push "+n.entry.offset, "add" // compute address of "id" declaration
			);
	if(!(n.entry.type instanceof ArrowTypeNode)) {	//se non è una funzione
		return nlJoin(retBasic, "lw");
	}else {
		return nlJoin(retBasic, 
				"stm",	// set $tm to popped value (with the aim of duplicating top of stack)
				"ltm",	// load Access Link (pointer to frame of function "id" declaration)
				"lw", 	// load address of "id" function
				"ltm",	// load Access Link (pointer to frame of function "id" declaration)
				"push 1",
				"sub"	// indir funzione (recuperato a offset ID - 1)
				);					
	}

}
@Override
public String visitNode(ClassNode n) {
	if (print) printNode(n,n.id);
	if(n.superID!=null) {
		dispatchTables.add(new ArrayList<>(dispatchTables.get(-n.superEntry.offset-2)));
	} else {			
		dispatchTables.add(new ArrayList<>());
	}
	
	for(MethodNode m:n.methodlist) {
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
public String visitNode(BoolNode n) {
	if (print) printNode(n,n.val.toString());
	return "push "+(n.val?1:0);
}

@Override
public String visitNode(IntNode n) {
	if (print) printNode(n,n.val.toString());
	return "push "+n.val;
}
}