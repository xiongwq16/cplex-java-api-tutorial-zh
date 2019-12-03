package examples;
/* --------------------------------------------------------------------------
 * File: QPex3.java
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
 * QPex3.java - Entering and modifying a QP problem
 *
 * Example QPex3.java illustrates how to enter and modify a QP problem 
 * by using linear quadratic expressions.
 */

import ilog.concert.*;
import ilog.cplex.*;


public class QPex3 {
   public static void main(String[] args) {
      try {
         // create a QP problem
         IloCplex cplex = new IloCplex();
         createQPModel(cplex);
         cplex.exportModel("qp1ex3.lp");

         // solve the QP problem
         solveAndPrint(cplex, "Solving the QP problem ...");

         // Modify the quadratic objective function
         modifyQuadObjective(cplex);
         cplex.exportModel("qp2ex3.lp");

         // solve the modified QP problem
         solveAndPrint(cplex, "Solving the modified QP problem ...");

         cplex.end();
      }
      catch (IloException e) {
         System.err.println("Concert exception '" + e + "' caught");
      }
   }


   // Creating a simple QP problem
   static IloLPMatrix createQPModel(IloMPModeler model) throws IloException {
      IloLPMatrix lp = model.addLPMatrix();

      double[]    lb = {0.0, 0.0, 0.0};
      double[]    ub = {40.0, Double.MAX_VALUE, Double.MAX_VALUE};
      IloNumVar[] x  = model.numVarArray(model.columnArray(lp, 3), lb, ub);
      int nvars = x.length;
      for (int j = 0; j < nvars; ++j)
         x[j].setName("x" +j);

      // - x0 +   x1 + x2 <= 20
      //   x0 - 3*x1 + x2 <= 30
      double[]   lhs = {-Double.MAX_VALUE, -Double.MAX_VALUE};
      double[]   rhs = {20.0, 30.0};
      double[][] val = {{-1.0,  1.0,  1.0},
                        { 1.0, -3.0,  1.0}};
      int[][]    ind = {{0, 1, 2},
                        {0, 1, 2}};
      lp.addRows(lhs, rhs, ind, val);

      // minimize - x0 - x1 - x2 + x0*x0 + x1*x1 + x0*x1 + x1*x0 
      IloLQNumExpr objExpr = model.lqNumExpr();
      for (int i = 0; i < nvars; ++i) {
         objExpr.addTerm(-1.0, x[i]);
         for (int j = 0; j < nvars; ++j) {
            objExpr.addTerm(1.0, x[i], x[j]);
         }
      }
      IloObjective obj = model.minimize(objExpr);
      model.add(obj);

      // Print out the objective function
      printObjective(obj);                    

      return lp;
   }

   
   // Modifying all quadratic terms x[i]*x[j] 
   // in the objective function.
   static void modifyQuadObjective(IloCplexModeler model) throws IloException {
      IloLPMatrix lp = (IloLPMatrix)model.LPMatrixIterator().next();
      int ncols = lp.getNcols();
      IloNumVar[] x = lp.getNumVars();
      IloObjective obj = model.getObjective();

      // Note that the quadratic expression in the objective
      // is normalized: i.e., for all i != j, terms 
      // c(i,j)*x[i]*x[j] + c(j,i)*x[j]*x[i] are normalized as
      // (c(i,j) + c(j,i)) * x[i]*x[j], or 
      // (c(i,j) + c(j,i)) * x[j]*x[i].
      // Therefore you can only modify one of the terms 
      // x[i]*x[j] or x[j]*x[i]. 
      // If you modify both x[i]*x[j] and x[j]*x[i], then 
      // the second modification will overwrite the first one.
      for (int i = 0; i < ncols; ++i) {
         model.setQuadCoef(obj, x[i], x[i], i*i);
         for (int j = 0; j < i; ++j)
            model.setQuadCoef(obj, x[i], x[j], -2.0*(i*j));
      }

      // Print out the objective function
      printObjective(obj);     
   }


   // Print out the objective function.
   // Note that the quadratic expression in the objective
   // is normalized: i.e., for all i != j, terms 
   // c(i,j)*x[i]*x[j] + c(j,i)*x[j]*x[i] is normalized as
   // (c(i,j) + c(j,i)) * x[i]*x[j], or 
   // (c(i,j) + c(j,i)) * x[j]*x[i].
   static void printObjective(IloObjective obj) throws IloException {
      System.out.println("obj: " + obj);

      // Count the number of linear terms 
      // in the objective function.
      int nlinterms = 0;
      IloLinearNumExprIterator lit = ((IloLQNumExpr)obj.getExpr()).linearIterator(); 
      while ( lit.hasNext() ) {
         lit.next();
         ++nlinterms;
      }

      // Count the number of quadratic terms 
      // in the objective function.
      int nquadterms = 0;
      int nquaddiag  = 0;  
      IloQuadNumExprIterator qit = ((IloLQNumExpr)obj.getExpr()).quadIterator(); 
      
      while ( qit.hasNext() ) {
         qit.next();
         ++nquadterms;
         IloNumVar var1 = qit.getNumVar1();
         IloNumVar var2 = qit.getNumVar2();
         if ( var1.equals(var2) ) ++nquaddiag;
      }
      
      System.out.println("number of linear terms in the objective             : " + nlinterms);
      System.out.println("number of quadratic terms in the objective          : " + nquadterms);
      System.out.println("number of diagonal quadratic terms in the objective : " + nquaddiag);
      System.out.println();
   }


   // Solve the current model and print results
   static void solveAndPrint(IloCplex cplex, String msg) throws IloException {
     System.out.println(msg);
     if ( cplex.solve() ) {
        System.out.println();   
        System.out.println("Solution status = " + cplex.getStatus());
        System.out.println("Solution value  = " + cplex.getObjValue());

        IloLPMatrix lp = (IloLPMatrix)cplex.LPMatrixIterator().next();
        double[] x = cplex.getValues(lp);
        int nvars = x.length;
        for (int j = 0; j < nvars; ++j) {
           System.out.println("Variable " + j +
                              ": Value = " + x[j]);
        }
     }
     System.out.println();
   }
      
}
