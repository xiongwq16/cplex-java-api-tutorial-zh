package examples;
/* --------------------------------------------------------------------------
 * File: AdMIPex6.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2001, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * AdMIPex6.java -- Solving a model by passing in a solution for the root node
 *                  and using that in a solve callback
 *
 * To run this example, command line arguments are required:
 *     java AdMIPex6  filename
 * where 
 *     filename  Name of the file, with .mps, .lp, or .sav
 *               extension, and a possible additional .gz 
 *               extension.
 * Example:
 *     java AdMIPex6  mexample.mps.gz
 */

import ilog.concert.*;
import ilog.cplex.*;


public class AdMIPex6 {
   static class Solve extends IloCplex.SolveCallback {
      boolean     _done = false;
      IloNumVar[] _vars;
      double[]    _x;
      Solve(IloNumVar[] vars, double[] x) { _vars = vars; _x = x; }
    
      public void main() throws IloException {
         if ( !_done ) {
            setStart(_x, _vars, null, null);
            _done = true;
         }
      }
   }

   public static void main(String[] args) {
      try {
         IloCplex cplex = new IloCplex();
       
         cplex.importModel(args[0]);
         IloLPMatrix lp = (IloLPMatrix)cplex.LPMatrixIterator().next();
       
         IloConversion relax = cplex.conversion(lp.getNumVars(),
                                                IloNumVarType.Float);
         cplex.add(relax);

         cplex.solve();
         System.out.println("Relaxed solution status = " + cplex.getStatus());
         System.out.println("Relaxed solution value  = " + cplex.getObjValue());

         double[] vals = cplex.getValues(lp.getNumVars());
         cplex.use(new Solve(lp.getNumVars(), vals));

         cplex.delete(relax);
       
	 cplex.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);
         if ( cplex.solve() ) {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solution value  = " + cplex.getObjValue());
         }
         cplex.end();
      }
      catch (IloException e) {
         System.err.println("Concert exception caught: " + e);
      }
   }
}
