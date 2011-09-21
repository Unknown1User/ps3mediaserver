package net.pms.encoders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JComponent;

import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAResource;
import net.pms.formats.Format;
import net.pms.io.InternalJavaProcessImpl;
import net.pms.io.OutputParams;
import net.pms.io.ProcessWrapper;
import net.pms.io.ProcessWrapperImpl;
import net.pms.PMS;

public class RAWThumbnailer extends Player {
	public static String ID = "rawthumbs"; //$NON-NLS-1$

	protected String[] getDefaultArgs() {
		return new String[]{"-e", "-c"}; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public String[] args() {
		return getDefaultArgs();

	}

	@Override
	public JComponent config() {
		return null;
	}

	@Override
	public String executable() {
		return PMS.getConfiguration().getDCRawPath();
	}

	@Override
	public String id() {
		return ID; //$NON-NLS-1$
	}

	@Override
	public ProcessWrapper launchTranscode(String fileName, DLNAResource dlna, DLNAMediaInfo media,
		OutputParams params) throws IOException {

		params.waitbeforestart = 1;
		params.minBufferSize = 1;
		params.maxBufferSize = 5;
		params.hidebuffer = true;

		if (media == null || media.thumb == null) {
			return null;
		}

		if (media.thumb.length == 0) {
			try {
				media.thumb = getThumbnail(params, fileName);
			} catch (Exception e) {
				return null;
			}
		}

		byte copy[] = new byte[media.thumb.length];
		System.arraycopy(media.thumb, 0, copy, 0, media.thumb.length);
		media.thumb = new byte[0];

		ProcessWrapper pw = new InternalJavaProcessImpl(new ByteArrayInputStream(copy));
		return pw;
	}

	@Override
	public String mimeType() {
		return "image/jpeg"; //$NON-NLS-1$
	}

	@Override
	public String name() {
		return "Raws Thumbnailer"; //$NON-NLS-1$
	}

	@Override
	public int purpose() {
		return MISC_PLAYER;
	}

	@Override
	public int type() {
		return Format.IMAGE;
	}

	public static byte[] getThumbnail(OutputParams params, String fileName) throws Exception {
		params.log = false;

		String cmdArray[] = new String[4];
		cmdArray[0] = PMS.getConfiguration().getDCRawPath();
		cmdArray[1] = "-e"; //$NON-NLS-1$
		cmdArray[2] = "-c"; //$NON-NLS-1$
		cmdArray[3] = fileName;
		ProcessWrapperImpl pw = new ProcessWrapperImpl(cmdArray, params);
		pw.run();


		InputStream is = pw.getInputStream(0);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int n = -1;
		byte buffer[] = new byte[4096];
		while ((n = is.read(buffer)) > -1) {
			baos.write(buffer, 0, n);
		}
		is.close();
		byte b[] = baos.toByteArray();
		baos.close();
		return b;
	}
}
