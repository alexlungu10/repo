package info.source4code.jsf.primefaces;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseId;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedBean
@SessionScoped
public class FileUploadController implements Serializable {

    private static final long serialVersionUID = 6279557265339478786L;

    private static final Logger LOGGER = LoggerFactory
            .getLogger(FileUploadController.class);

    // maximum number of files that can be uploaded
    private static final int FILE_LIMIT = 3;
    // location to which the files will be written
    private static final String FILE_SUBMIT_PATH = "target/tmp/flags/";
    private static final String TEMP_EXTENSION = "_tmp/";

    private List<UploadedFile> uploadedFiles;
    private String uploadId;
    private int currentFileLimit;

    @PostConstruct
    public void init() {
        uploadedFiles = new ArrayList<UploadedFile>();
        uploadId = UUID.randomUUID().toString();
        currentFileLimit = FILE_LIMIT;

        LOGGER.info("fileupload initialized with uploadId: {}", uploadId);
    }

    public List<UploadedFile> getUploadedFiles() {
        return uploadedFiles;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void handleFileUpload(FileUploadEvent event) {

        FacesMessage message = null;

        try {
            // workaround for event.getFile().getContents() returning NULL
            byte[] contents = IOUtils.toByteArray(event.getFile()
                    .getInputstream());

            UploadedFile uploadedFile = new UploadedFile(event.getFile()
                    .getFileName(), event.getFile().getContentType(), event
                    .getFile().getSize(), contents);
            uploadedFiles.add(uploadedFile);

            // per uploaded file lower the currentFileLimit
            currentFileLimit--;

            LOGGER.debug("uploaded: {}", uploadedFile);
            message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "File uploaded", event.getFile().getFileName()
                            + " is successfuly uploaded.");
        } catch (IOException ioException) {
            LOGGER.error("handleFileUpload", ioException);
            message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Upload Failed", event.getFile().getFileName()
                            + " is not uploaded.");
        }

        FacesContext.getCurrentInstance().addMessage(null, message);
    }

    public void removeUploadedFile(String id) {
        // find the associated UploadedFile
        UploadedFile uploadedFile = findUploadedFile(id);

        if (uploadedFile != null) {
            uploadedFiles.remove(uploadedFile);

            // per removed file increase the currentFileLimit
            currentFileLimit++;

            LOGGER.debug("removed: {}", uploadedFile);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "File removed", uploadedFile.getName()
                            + " is successfuly removed.");
            FacesContext.getCurrentInstance().addMessage(null, message);
        }
    }

    public String submitUploadedFiles() {
        String redirect = "";

        // check if file(s) were uploaded if not return a warning
        if (!uploadedFiles.isEmpty()) {
            // write the file(s) to disk
            try {
                String targetDirectory = FILE_SUBMIT_PATH + uploadId;
                int index = 1;

                Iterator<UploadedFile> iterator = uploadedFiles.iterator();
                while (iterator.hasNext()) {
                    UploadedFile uploadedFile = (UploadedFile) iterator.next();

                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                            uploadedFile.getContents());

                    File targetFile = new File(targetDirectory + TEMP_EXTENSION
                            + index + "_" + uploadedFile.getName());
                    FileUtils.copyInputStreamToFile(byteArrayInputStream,
                            targetFile);
                    LOGGER.info("submitted: {}", uploadedFile);

                    index++;
                }

                // rename the temporary directory
                new File(targetDirectory + TEMP_EXTENSION).renameTo(new File(
                        targetDirectory));

                // set redirect and pass the uploadId
                redirect = "submitted?faces-redirect=true&uploadId=" + uploadId;
                // reset before redirect
                init();

            } catch (IOException ioException) {
                LOGGER.error("not able to submit files", ioException);

                FacesMessage message = new FacesMessage(
                        FacesMessage.SEVERITY_ERROR, "Error occured",
                        "File(s) have not been submitted!");
                FacesContext.getCurrentInstance().addMessage(null, message);
            }
        } else {
            LOGGER.warn("not submitted as no file(s) have been uploaded");
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_WARN,
                    "Not submitted", "No file(s) have been uploaded.");
            FacesContext.getCurrentInstance().addMessage(null, message);
        }

        return redirect;
    }

    public String getCurrentFileLimit() {
        int result = currentFileLimit - uploadedFiles.size();

        // avoid 0 as this means an unlimited number of files can be uploaded
        if (result <= 0) {
            result = -1;
        }

        LOGGER.debug("current file limit: {}", result);
        return Integer.toString(result);
    }

    public StreamedContent getImage() throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();

        if (context.getCurrentPhaseId() == PhaseId.RENDER_RESPONSE) {
            /*
             * browser is rendering the view, return a stub StreamedContent so
             * that it will generate the correct URL
             */
            return new DefaultStreamedContent();
        } else {
            /*
             * browser is requesting the image, return a real StreamedContent
             * with the image
             */

            // retrieve the id from the request
            String id = context.getExternalContext().getRequestParameterMap()
                    .get("uploadedFileId");
            // find the associated UploadedFile
            UploadedFile uploadedFile = findUploadedFile(id);

            // do not use InputStream as it is not Serializable
            return new DefaultStreamedContent(new ByteArrayInputStream(
                    uploadedFile.getContents()), uploadedFile.getContentType());
        }
    }

    private UploadedFile findUploadedFile(String id) {
        UploadedFile result = null;

        Iterator<UploadedFile> iterator = uploadedFiles.iterator();
        while (iterator.hasNext()) {
            UploadedFile uploadedFile = (UploadedFile) iterator.next();

            if (id.equals(uploadedFile.getId())) {
                result = uploadedFile;
                break;
            }
        }

        return result;
    }
}
