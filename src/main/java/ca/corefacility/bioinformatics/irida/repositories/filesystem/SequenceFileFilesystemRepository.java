/*
 * Copyright 2013 Franklin Bristow <franklin.bristow@phac-aspc.gc.ca>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.corefacility.bioinformatics.irida.repositories.filesystem;

import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.InvalidPropertyException;
import ca.corefacility.bioinformatics.irida.exceptions.StorageException;
import ca.corefacility.bioinformatics.irida.model.FieldMap;
import ca.corefacility.bioinformatics.irida.model.SequenceFile;
import ca.corefacility.bioinformatics.irida.model.enums.Order;
import ca.corefacility.bioinformatics.irida.model.roles.impl.Identifier;
import ca.corefacility.bioinformatics.irida.repositories.CRUDRepository;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A repository class for managing {@link SequenceFile} files on the file
 * system.
 *
 * @author Franklin Bristow <franklin.bristow@phac-aspc.gc.ca>
 */
public class SequenceFileFilesystemRepository implements CRUDRepository<Identifier, SequenceFile> {

    private static final Logger logger = LoggerFactory.getLogger(SequenceFileFilesystemRepository.class);
    private final Path BASE_DIRECTORY;

    public SequenceFileFilesystemRepository(String baseDirectory) {
        this(FileSystems.getDefault().getPath(baseDirectory));
    }

    public SequenceFileFilesystemRepository(Path baseDirectory) {
        this.BASE_DIRECTORY = baseDirectory;
    }

    /**
     * Get the appropriate directory for the {@link SequenceFile}.
     *
     * @param id the {@link Identifier} of the {@link SequenceFile}.
     * @return the {@link Path} for the {@link SequenceFile}.
     */
    private Path getSequenceFileDir(Identifier id) {
        return BASE_DIRECTORY.resolve(id.getIdentifier());
    }

    /**
     * The {@link SequenceFile} *must* have an identifier before being passed to
     * this method, because the identifier is used as an internal directory
     * name.
     *
     * @param object the {@link SequenceFile} to store.
     * @return a reference to the {@link SequenceFile} with the stored path.
     * @throws IllegalArgumentException if the {@link SequenceFile} does not
     *                                  have an identifier.
     */
    @Override
    public SequenceFile create(SequenceFile object) throws IllegalArgumentException {
        if (object.getIdentifier() == null
                || Strings.isNullOrEmpty(object.getIdentifier().getIdentifier())) {
            throw new IllegalArgumentException("Identifier is required.");
        }
        Path sequenceFileDir = getSequenceFileDir(object.getIdentifier());
        Path target = sequenceFileDir.resolve(object.getFile().getFileName());
        try {
            Files.createDirectory(sequenceFileDir);
            logger.debug("Created directory: [" + sequenceFileDir.toString() + "]");
            Files.move(object.getFile(), target);
        } catch (IOException e) {
            e.printStackTrace();
            throw new StorageException("Failed to move file into new directory.");
        }
        object.setFile(target);
        return object;
    }

    /**
     * This method is not supported by {@link SequenceFileFilesystemRepository}
     * and will throw an {@link UnsupportedOperationException}.
     *
     * @param id the file to load.
     * @return the {@link SequenceFile} with a reference to the file.
     * @throws EntityNotFoundException if the file cannot be found.
     * @see ca.corefacility.bioinformatics.irida.repositories.sesame.SequenceFileSesameRepository
     */
    @Override
    public SequenceFile read(Identifier id) throws EntityNotFoundException {
        throw new UnsupportedOperationException("SequenceFile file reference "
                + "should be populated by SequenceFileSesameRepository.");
    }

    private Path updateFilesystemFile(Identifier id, Path object) throws IllegalArgumentException {
        if (id == null || Strings.isNullOrEmpty(id.getIdentifier())) {
            throw new IllegalArgumentException("Identifier is required.");
        }

        // the sequence file directory must previously exist, if not, then
        // we're doing something very weird.
        Path sequenceFileDir = getSequenceFileDir(id);
        if (!Files.exists(sequenceFileDir)) {
            throw new IllegalArgumentException("The directory for this "
                    + "SequenceFile does not exist, has it been persisted "
                    + "before?");
        }

        // the directory exists. does the target file exist? if so, we don't
        // want to overwrite the file. We'll rename the existing file with the
        // current date appended to the end so that we're retaining existing
        // file names.
        Path target = sequenceFileDir.resolve(object.getFileName());
        if (Files.exists(target)) {
            String destination = target.getFileName().toString() + "-" + new Date().getTime();

            // rename the existing file
            try {
                Files.move(target, target.resolveSibling(destination));
            } catch (IOException e) {
                throw new StorageException("Couldn't rename existing file.");
            }
        }

        // now handle storing the file as before:
        try {
            Files.move(object, target);
        } catch (IOException e) {
            throw new StorageException("Couldn't move updated file to existing directory.");
        }

        return target;
    }

    @Override
    public List<SequenceFile> list() {
        throw new UnsupportedOperationException("Files cannot be listed independently.");
    }

    @Override
    public List<SequenceFile> list(int page, int size, String sortProperty, Order order) {
        throw new UnsupportedOperationException("Files cannot be listed independently.");
    }

    @Override
    public Integer count() {
        throw new UnsupportedOperationException("Files cannot be counted");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SequenceFile update(Identifier id, Map<String, Object> updatedFields) throws InvalidPropertyException, SecurityException {
        SequenceFile file = new SequenceFile();
        if (updatedFields.containsKey("file")) {
            Path updatedFile = (Path) updatedFields.get("file");
            Path target = updateFilesystemFile(id, updatedFile);
            file.setFile(target);
        }

        return file;
    }

    @Override
    public void delete(Identifier id) throws EntityNotFoundException {
        throw new UnsupportedOperationException("Files cannot be deleted.");

    }

    @Override
    public Boolean exists(Identifier id) {
        throw new UnsupportedOperationException("SequenceFile file exists "
                + "should be populated by SequenceFileSesameRepository.");
    }

    /**
     * Reading multiple files will not be supported
     */
    @Override
    public Collection<SequenceFile> readMultiple(Collection<Identifier> idents) {
        throw new UnsupportedOperationException("Reading multiple files will not be supported.");
    }

    @Override
    public List<FieldMap> listMappedFields(List<String> fields) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public List<FieldMap> listMappedFields(List<String> fields, int page, int size, String sortProperty, Order order) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
}
