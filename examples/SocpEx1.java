package examples;
/* --------------------------------------------------------------------------
 * File: SocpEx1.java
 * Version 12.9.0
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2003, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 */

/* SocpEx1.java -- Solve a second order cone program to optimality, fetch
 *                 the dual values and test that the primal and dual solution
 *                 vectors returned by CPLEX satisfy the KKT conditions.
 *                 The problems that this code can handle are second order
 *                 cone programs in standard form. A second order cone
 *                 program in standard form is a problem of the following
 *                 type (c' is the transpose of c):
 *                   min c1'x1 + ... + cr'xr
 *                     A1 x1 + ... + Ar xr = b
 *                     xi in second order cone (SOC)
 *                 where xi is a vector of length ni. Note that the
 *                 different xi are orthogonal. The constraint "xi in SOC"
 *                 for xi=(xi[1], ..., xi[ni]) is
 *                     xi[1] >= |(xi[2],...,xi[ni])|
 *                 where |.| denotes the Euclidean norm. In CPLEX such a
 *                 constraint is formulated as
 *                     -xi[1]^2 + xi[2]^2 + ... + xi[ni]^2 <= 0
 *                      xi[1]                              >= 0
 *                                xi[2], ..., xi[ni] free
 *                 Note that if ni = 1 then the second order cone constraint
 *                 reduces to xi[1] >= 0.
 */

import ilog.cplex.*;
import ilog.concert.*;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Collection;

public final class SocpEx1 {
   /* Tolerance for testing KKT conditions. */
   private static final double TESTTOL = 1e-9;
   /* Tolerance for barrier convergence. */
   private static final double CONVTOL = 1e-9;

   /* A comparator to compare two instances of IloAddable by name,
    */
   private static final Comparator<IloAddable> AddableCompare = new Comparator<IloAddable>() {
      public int compare(IloAddable a1, IloAddable a2) {
         return a1.getName().compareTo(a2.getName());
      }
   };

   /* Marks variables that are in cone constraints but are not the cone
    * constraint's cone head. */
   private static IloRange NOT_CONE_HEAD;
   /* Marks variables that are not in any cone constraint. */
   private static IloRange NOT_IN_CONE;

   // NOTE: CPLEX does not provide a function to directly get the dual
   //       multipliers for second order cone constraint.
   //       Example QCPDual.java illustrates how the dual multipliers for a
   //       quadratic constraint can be computed from that constraint's
   //       slack.
   //       However, for SOCP we can do something simpler: we can read those
   //       multipliers directly from the dual slacks for the
   //       cone head variables. For a second order cone constraint
   //          x[1] >= |(x[2], ..., x[n])|
   //       the dual multiplier is the dual slack value for x[1].
   /** Compute dual multipliers for second order cone constraints.
    * @param cplex  The IloCplex instance that holds the optimal solution.
    * @param vars   Collection with <em>all</em> variables in the model.
    * @param rngs   Collection with <em>all</em> variables in the model.
    * @param dslack If not <code>null</code> the function will store the full
    *               dual slack vector in this map.
    * @return A map that has a dual multiplier for each second order cone
    *         constraint in <code>rngs</code>.
    */
   private static Map<IloRange,Double> getsocpconstrmultipliers(IloCplex cplex, Collection<IloNumVar> vars, Collection<IloRange> rngs, Map<IloNumVar,Double> dslack) throws IloException {
      // Compute full dual slack.
      final Map<IloNumVar,Double> dense = new TreeMap<IloNumVar,Double>(AddableCompare);
      for (IloNumVar v : vars) {
         dense.put(v, cplex.getReducedCost(v));
      }
      for (IloRange r : rngs) {
         IloNumExpr e = r.getExpr();
         if ( (e instanceof IloQuadNumExpr) && ((IloQuadNumExpr)e).quadIterator().hasNext() )
         {
            // Quadratic constraint: pick up dual slack vector.
            for (IloLinearNumExprIterator it = cplex.getQCDSlack(r).linearIterator(); it.hasNext(); /* nothing */) {
               IloNumVar v = it.nextNumVar();
               dense.put(v, dense.get(v) + it.getValue());
            }
         }
      }

      // Find the cone head variables and pick up the dual slacks for them.
      final Map<IloRange,Double> socppi = new TreeMap<IloRange,Double>(AddableCompare);
      for (IloRange r : rngs) {
         IloNumExpr e = r.getExpr();
         if ((e instanceof IloQuadNumExpr) && ((IloQuadNumExpr)e).quadIterator().hasNext()) {
            for (IloQuadNumExprIterator it = ((IloQuadNumExpr)e).quadIterator(); it.hasNext(); /* nothing */) {
               it.next();
               if (it.getValue() < 0) {
                  socppi.put(r, dense.get(it.getNumVar1()));
                  break;
               }
            }
         }
      }

      // Fill in the dense slack if the user asked for it.
      if (dslack != null) {
         dslack.clear();
         for (IloNumVar v : dense.keySet())
            dslack.put(v, dense.get(v));
      }

      return socppi;
   }

   /* Test KKT conditions on the solution.
    * The function returns true if the tested KKT conditions are satisfied
    * and false otherwise.
    */
   private static boolean checkkkt (IloCplex cplex,
                                    IloObjective obj,
                                    Collection<IloNumVar> vars,
                                    Collection<IloRange> rngs,
                                    Map<IloNumVar,IloRange> cone, double tol) throws IloException {
      PrintStream err = cplex.output();
      PrintStream out = cplex.output();

      Map<IloNumVar,Double> dslack = new TreeMap<IloNumVar,Double>(AddableCompare);
      Map<IloNumVar,Double> x  = new TreeMap<IloNumVar,Double>(AddableCompare);
      Map<IloRange,Double> pi = null;
      Map<IloRange,Double> slack = new TreeMap<IloRange,Double>(AddableCompare);

      // Read primal and dual solution information.
      for (IloNumVar v : vars)
         x.put(v, cplex.getValue(v));
      pi = getsocpconstrmultipliers(cplex, vars, rngs, dslack);
      for (IloRange r : rngs) {
         slack.put(r, cplex.getSlack(r));
         IloNumExpr e = r.getExpr();
         if ( !(e instanceof IloQuadNumExpr) || !((IloQuadNumExpr)e).quadIterator().hasNext()) {
            // Linear constraint: get the dual value.
            pi.put(r, cplex.getDual(r));
         }
      }

      // Print out the data we just fetched.
      out.print("x      = [");
      for (IloNumVar v : x.keySet())
         out.format(" %7.3f", x.get(v));
      out.println(" ]");
      out.print("dslack = [");
      for (IloNumVar v : dslack.keySet())
         out.format(" %7.3f", dslack.get(v));
      out.println(" ]");
      out.print("pi     = [");
      for (IloRange r : pi.keySet())
         out.format(" %7.3f", pi.get(r));
      out.println(" ]");
      out.print("slack  = [");
      for (IloRange r : slack.keySet())
         out.format(" %7.3f", slack.get(r));
      out.println(" ]");

      // Test primal feasibility.
      // This example illustrates the use of dual vectors returned by CPLEX
      // to verify dual feasibility, so we do not test primal feasibility
      // here.

      // Test dual feasibility.
      // We must have
      // - for all <= constraints the respective pi value is non-negative,
      // - for all >= constraints the respective pi value is non-positive,
      // - the dslack value for all non-cone variables must be non-negative.
      // Note that we do not support ranged constraints here.
      for (IloNumVar v : vars) {
         if ( cone.get(v) == NOT_IN_CONE && dslack.get(v) < -tol ) {
            err.println("Dual multiplier for " + v + " is not feasible: " + dslack.get(v));
            return false;
         }
      }
      for (IloRange r : rngs) {
         if ( Math.abs (r.getLB() - r.getUB()) <= tol ) {
            // Nothing to check for equality constraints.
         }
         else if ( r.getLB() > -Double.MAX_VALUE && pi.get(r) > tol ) {
            err.println("Dual multiplier " + pi.get(r) + " for >= constraint");
            err.println(" " + r);
            err.println("not feasible.");
            return false;
         }
         else if ( r.getUB() < Double.MAX_VALUE && pi.get(r) < -tol ) {
            err.println("Dual multiplier " + pi.get(r) + " for <= constraint");
            err.println(" " + r);
            err.println("not feasible.");
            return false;
         }
      }

      // Test complementary slackness.
      // For each constraint either the constraint must have zero slack or
      // the dual multiplier for the constraint must be 0. We must also
      // consider the special case in which a variable is not explicitly
      //  contained in a second order cone constraint.
      for (IloNumVar v : vars) {
         if ( cone.get(v) == NOT_IN_CONE ) {
            if ( Math.abs(x.get(v)) > tol && dslack.get(v) > tol ) {
               err.println("Invalid complementary slackness for " + v + ":");
               err.println(" " + x.get(v) + " and " + dslack.get(v));
               return false;
            }
         }
      }
      for (IloRange r : rngs) {
         if ( Math.abs(slack.get(r)) > tol && Math.abs(pi.get(r)) > tol ) {
            err.println("Invalid complementary slackness for");
            err.println(" " + r);
            err.println(" " + slack.get(r) + " and " + pi.get(r));
            return false;
         }
      }

      // Test stationarity.
      // We must have
      //  c - g[i]'(X)*pi[i] = 0
      // where c is the objective function, g[i] is the i-th constraint of the
      // problem, g[i]'(x) is the derivate of g[i] with respect to x and X is the
      // optimal solution.
      // We need to distinguish the following cases:
      // - linear constraints g(x) = ax - b. The derivative of such a
      //   constraint is g'(x) = a.
      // - second order constraints g(x[1],...,x[n]) = -x[1] + |(x[2],...,x[n])|
      //   the derivative of such a constraint is
      //     g'(x) = (-1, x[2]/|(x[2],...,x[n])|, ..., x[n]/|(x[2],...,x[n])|
      //   (here |.| denotes the Euclidean norm).
      // - bound constraints g(x) = -x for variables that are not explicitly
      //   contained in any second order cone constraint. The derivative for
      //   such a constraint is g'(x) = -1.
      // Note that it may happen that the derivative of a second order cone
      // constraint is not defined at the optimal solution X (this happens if
      // X=0). In this case we just skip the stationarity test.
      Map<IloNumVar,Double> sum = new TreeMap<IloNumVar,Double>(AddableCompare);
      for (IloNumVar v : vars)
         sum.put(v, 0.0);
      for (IloLinearNumExprIterator it = ((IloLinearNumExpr)cplex.getObjective().getExpr()).linearIterator(); it.hasNext(); /* nothing */) {
         IloNumVar v = it.nextNumVar();
         sum.put(v, it.getValue());
      }

      for (IloNumVar v : vars) {
         if ( cone.get(v) == NOT_IN_CONE )
            sum.put(v, sum.get(v) - dslack.get(v));
      }

      for (IloRange r : rngs) {
         IloNumExpr e = r.getExpr();
         if ( (e instanceof IloQuadNumExpr) && ((IloQuadNumExpr)e).quadIterator().hasNext() ) {
            double norm = 0.0;
            for (IloQuadNumExprIterator q = ((IloQuadNumExpr)e).quadIterator(); q.hasNext(); /* nothing */) {
               q.next();
               if ( q.getValue() > 0 )
                  norm += x.get(q.getNumVar1()) * x.get(q.getNumVar1());
            }
            norm = Math.sqrt(norm);
            if ( Math.abs(norm) <= tol ) {
               cplex.warning().println("Cannot check stationarity at non-differentiable point");
               return true;
            }
            for (IloQuadNumExprIterator q = ((IloQuadNumExpr)e).quadIterator(); q.hasNext(); /* nothing */) {
               q.next();
               IloNumVar v = q.getNumVar1();
               if ( q.getValue() < 0 )
                  sum.put(v, sum.get(v) - pi.get(r));
               else if ( q.getValue() > 0 )
                  sum.put(v, sum.get(v) + pi.get(r) * x.get(v) / norm);
            }
         }
         else if ( e instanceof IloLinearNumExpr ) {
            for (IloLinearNumExprIterator it = ((IloLinearNumExpr)e).linearIterator(); it.hasNext(); /* nothing */) {
               IloNumVar v = it.nextNumVar();
               sum.put(v, sum.get(v) - pi.get(r) * it.getValue());
            }
         }
      }

      // Now test that all elements in sum[] are 0.
      for (IloNumVar v : vars) {
         if ( Math.abs(sum.get(v)) > tol ) {
            err.println("Invalid stationarity " + sum.get(v) + " for " + v);
            return false;
         }
      }
      
      return true;   
   }

   // This function creates the following model:
   //   Minimize
   //    obj: x1 + x2 + x3 + x4 + x5 + x6
   //   Subject To
   //    c1: x1 + x2      + x5      = 8
   //    c2:           x3 + x5 + x6 = 10
   //    q1: [ -x1^2 + x2^2 + x3^2 ] <= 0
   //    q2: [ -x4^2 + x5^2 ] <= 0
   //   Bounds
   //    x2 Free
   //    x3 Free
   //    x5 Free
   //   End
   // which is a second order cone program in standard form.
   private static IloObjective createmodel(IloCplex cplex,
                                           Collection<IloNumVar> vars,
                                           Collection<IloRange> rngs,
                                           Map<IloNumVar,IloRange> cone) throws IloException {
      IloNumVar x1 = cplex.numVar(                        0, Double.POSITIVE_INFINITY, "x1");
      IloNumVar x2 = cplex.numVar( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x2");
      IloNumVar x3 = cplex.numVar( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x3");
      IloNumVar x4 = cplex.numVar(                        0, Double.POSITIVE_INFINITY, "x4");
      IloNumVar x5 = cplex.numVar( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x5");
      IloNumVar x6 = cplex.numVar(                        0, Double.POSITIVE_INFINITY, "x6");
      
      IloObjective obj = cplex.addMinimize(cplex.sum(cplex.sum(cplex.sum(x1, x2),
                                                               cplex.sum(x3, x4)),
                                                     cplex.sum(x5, x6)),
                                           "obj");

      IloRange c1 = cplex.addEq(cplex.sum(x1, cplex.sum(x2, x5)), 8, "c1");
      IloRange c2 = cplex.addEq(cplex.sum(x3, cplex.sum(x5, x6)), 10, "c2");
   
      IloRange q1 = cplex.addLe(cplex.sum(cplex.prod(-1, cplex.prod(x1, x1)),
                                          cplex.sum(cplex.prod(x2, x2),
                                                    cplex.prod(x3, x3))), 0,
                                "q1");
      cone.put(x1, q1);
      cone.put(x2, NOT_CONE_HEAD);
      cone.put(x3, NOT_CONE_HEAD);
      IloRange q2 = cplex.addLe(cplex.sum(cplex.prod(-1, cplex.prod(x4, x4)),
                                          cplex.prod(x5, x5)), 0,
                                "q2");
      cone.put(x4, q2);
      cone.put(x5, NOT_CONE_HEAD);

      cone.put(x6, NOT_IN_CONE);

      vars.add(x1);
      vars.add(x2);
      vars.add(x3);
      vars.add(x4);
      vars.add(x5);
      vars.add(x6);

      rngs.add(c1);
      rngs.add(c2);
      rngs.add(q1);
      rngs.add(q2);

      return obj;
   }

   public static void main(String[] args) {
      Map<IloNumVar,IloRange> cone = new TreeMap<IloNumVar,IloRange>(AddableCompare);
      int retval = -1;
      IloCplex cplex = null;
      try {
         cplex = new IloCplex();

         // Initialize the two special (empty) marker ranges.
         NOT_CONE_HEAD = cplex.range(0, 0);
         NOT_IN_CONE = cplex.range(0, 0);

         // Create the model.
         IloObjective obj;
         Collection<IloNumVar> vars = new Vector<IloNumVar>();
         Collection<IloRange> rngs = new Vector<IloRange>();
         obj = createmodel(cplex, vars, rngs, cone);

         // Apply parameter settings.
         cplex.setParam(IloCplex.Param.Barrier.QCPConvergeTol, CONVTOL);

         // Solve the problem. If we cannot find an _optimal_ solution then
         // there is no point in checking the KKT conditions and we throw an
         // exception.
         if ( !cplex.solve() || cplex.getStatus() != IloCplex.Status.Optimal )
            throw new RuntimeException("Failed to solve problem to optimality");

         // Test the KKT conditions on the solution.
         if ( !checkkkt (cplex, obj, vars, rngs, cone, TESTTOL) ) {
            cplex.output().println("Testing of KKT conditions failed.");
         }
         else {
            cplex.output().println("KKT conditions are satisfied.");
            retval = 0;
         }
      } catch (IloException e) {
         if ( cplex != null ) {
            cplex.output().println("IloException: " + e);
            e.printStackTrace(cplex.output());
         }
         else {
            System.err.println("IloException: " + e);
            e.printStackTrace();
         }
         retval = -1;
      } finally {
         if ( cplex != null )
            cplex.end();
      }
      System.exit(retval);
   }
}
