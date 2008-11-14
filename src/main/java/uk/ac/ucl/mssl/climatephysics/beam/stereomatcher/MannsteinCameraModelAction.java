package uk.ac.ucl.mssl.climatephysics.beam.stereomatcher;

import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;


	public class MannsteinCameraModelAction extends AbstractVisatAction {
		private SingleTargetProductDialog dialog;
		public void actionPerformed(CommandEvent event){
			if (null == dialog){
				dialog = new DefaultSingleTargetProductDialog(
						"MannsteinCameraModel",
						getAppContext(),
						"MSSL Mannstein Camera Model",
						null);
			}
			dialog.show();
		}
		
	}
	

